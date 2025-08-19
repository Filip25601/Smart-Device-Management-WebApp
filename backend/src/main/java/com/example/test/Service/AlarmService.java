package com.example.test.Service;

import com.example.test.Model.Alarm;
import com.example.test.Model.User;
import com.google.api.core.ApiFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.database.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Service
public class AlarmService {
    private final DatabaseReference databaseReference;

    public AlarmService() {
        this.databaseReference = FirebaseDatabase.getInstance().getReference("alarm");
    }

    public String createAlarm(String uid, String name) throws FirebaseAuthException {
        String alarmId = databaseReference.push().getKey();
        if (alarmId == null) {
            throw new IllegalArgumentException("Alarm id is null");
        }
        Alarm alarm = new Alarm();
        alarm.setId(alarmId);
        alarm.setLastPing(System.currentTimeMillis());
        alarm.setName(name);
        alarm.setIsWorking(true);
        alarm.setOwnerUid(uid);
        alarm.setBreakIn(false);

        FirebaseDatabase.getInstance().getReference("user")
                .child(Base64.getUrlEncoder().encodeToString(FirebaseAuth.getInstance().getUser(uid).getEmail().getBytes(StandardCharsets.UTF_8)))
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        User user = snapshot.getValue(User.class);
                        if (user != null && user.getClient() != null) {
                            alarm.setClient(user.getClient());
                        }
                        databaseReference.child(alarmId).setValueAsync(alarm);
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        System.err.println("Client fetch failed");
                    }
                });

        databaseReference.child(alarmId).setValueAsync(alarm);
        
        try {
            ApiFuture<UserRecord> future = FirebaseAuth.getInstance().getUserAsync(uid);
            UserRecord userRecord = future.get();
            String email = userRecord.getEmail();
            if(email != null) {
                String safeEmail = Base64.getUrlEncoder().encodeToString(email.getBytes(StandardCharsets.UTF_8));
                FirebaseDatabase.getInstance().getReference("alarm_users")
                        .child(alarmId)
                        .child(safeEmail)
                        .setValueAsync(true);
            }
        } catch (Exception e){
            System.err.println("Error getting email"+ e.getMessage());
        }
        return alarmId;
    }

    public void deleteAlarm(String id) {
        databaseReference.child(id).removeValueAsync();
        FirebaseDatabase.getInstance().getReference("alarm_users").child(id).removeValueAsync();
        System.out.println("Alarm deleted");
    }

    public void turnOnAlarm(String id) throws ExecutionException, InterruptedException {
        checkAccess(id);
        databaseReference.child(id).child("isWorking").setValueAsync(true);
        logAlarmEvent(id,"Alarm turned on");
        System.out.println("Alarm turned on");
    }

    public void turnOffAlarm(String id) throws ExecutionException, InterruptedException {
        checkAccess(id);
        databaseReference.child(id).child("isWorking").setValueAsync(false);
        databaseReference.child(id).child("breakIn").setValueAsync(false);
        logAlarmEvent(id,"Alarm turned off");
        System.out.println("Alarm turned off");
    }

    public void grantAccess(String id, String uid, String targetEmail) throws ExecutionException, InterruptedException, TimeoutException {
//        Alarm alarm = getAlarmById(id);
//        if(!alarm.getOwnerUid().equals(uid)) {
//            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Only admin can grant access");
//        }
        String safeEmail = Base64.getUrlEncoder().encodeToString(targetEmail.getBytes(StandardCharsets.UTF_8));
        FirebaseDatabase.getInstance().getReference("alarm_users").child(id).child(safeEmail).setValueAsync(true);
    }


    public Alarm getAlarmById(String alarmId) throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Alarm> future = new CompletableFuture<>();
        databaseReference.child(alarmId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Alarm alarm = snapshot.getValue(Alarm.class);
                if (alarm != null) {
                    alarm.setId(snapshot.getKey());
                    future.complete(alarm);
                }
                else {
                    future.completeExceptionally(new RuntimeException("Alarm not found"));
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                future.completeExceptionally(new RuntimeException("Firebase read failed"));
            }
        });
        return future.get();
    }

    private void checkAccess (String alarmId) throws ExecutionException, InterruptedException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        //admin provjera
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return;
        }

        String email = null;
        if (authentication.getCredentials() instanceof FirebaseToken firebaseToken) {
            email = firebaseToken.getEmail();
        }
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"User not found");
        }
        String safeEmail = Base64.getUrlEncoder().encodeToString(email.getBytes(StandardCharsets.UTF_8));

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        DatabaseReference alarmUsersRef = FirebaseDatabase.getInstance().getReference("alarm_users").child(alarmId).child(safeEmail);

        alarmUsersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                future.complete(snapshot.exists());
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                future.completeExceptionally(new RuntimeException("Firebase read failed"));
            }
        });
        if (!future.get()){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Access forbidden to the alarm");
        }
    }

    public void revokeAlarm(String id, String uid, String targetEmail) throws ExecutionException, InterruptedException, TimeoutException {
//        Alarm alarm = getAlarmById(id);
//        if(!alarm.getOwnerUid().equals(uid)) {
//            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Only owner can revoke access");
//        }
        String safeEmail = Base64.getUrlEncoder().encodeToString(targetEmail.getBytes(StandardCharsets.UTF_8));
        FirebaseDatabase.getInstance().getReference("alarm_users").child(id).child(safeEmail).removeValueAsync();
    }

    private void logAlarmEvent(String alarmId, String message) {
        DatabaseReference logRef = databaseReference.child(alarmId).child("logs").push();
        Map<String, Object> logData = new HashMap<>();
        logData.put("message", message);
        logData.put("timestamp", System.currentTimeMillis());
        logRef.setValueAsync(logData);
    }

    public void endBreakIn(String id) {
        databaseReference.child(id).child("breakIn").setValueAsync(false);
        logAlarmEvent(id,"BreakIn ended");
        System.out.println("BreakIn ended");
    }

}
