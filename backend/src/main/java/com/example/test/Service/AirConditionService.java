package com.example.test.Service;

import com.example.test.Model.AirCondition;
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
public class AirConditionService {
    private final DatabaseReference databaseReference;

    public AirConditionService() {
        this.databaseReference = FirebaseDatabase.getInstance().getReference("airCondition");
    }

    public String createAirCondition(String uid, String name) throws FirebaseAuthException {
        String airConditionId = databaseReference.push().getKey();
        if (airConditionId == null) {
            throw new IllegalArgumentException("Air Condition id is null");
        }
        AirCondition airCondition = new AirCondition();
        airCondition.setId(airConditionId);
        airCondition.setName(name);
        airCondition.setIsWorking(true);
        airCondition.setOwnerUid(uid);
        airCondition.setLastPing(System.currentTimeMillis());
        airCondition.setMode("cold");
        airCondition.setCurrentTemperature(22.0);
        airCondition.setDesiredTemperature(22.0);


        FirebaseDatabase.getInstance().getReference("user")
                .child(Base64.getUrlEncoder().encodeToString(FirebaseAuth.getInstance().getUser(uid).getEmail().getBytes(StandardCharsets.UTF_8)))
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        User user = snapshot.getValue(User.class);
                        if (user != null && user.getClient() != null) {
                            airCondition.setClient(user.getClient());
                        }
                        databaseReference.child(airConditionId).setValueAsync(airCondition);
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        System.err.println("Client fetch failed");
                    }
                });

        databaseReference.child(airConditionId).setValueAsync(airCondition);

        try {
            ApiFuture<UserRecord> future = FirebaseAuth.getInstance().getUserAsync(uid);
            UserRecord userRecord = future.get();
            String email = userRecord.getEmail();
            if(email != null) {
                String safeEmail = Base64.getUrlEncoder().encodeToString(email.getBytes(StandardCharsets.UTF_8));
                FirebaseDatabase.getInstance().getReference("airCondition_users")
                        .child(airConditionId)
                        .child(safeEmail)
                        .setValueAsync(true);
            }
        } catch (Exception e){
            System.err.println("Error getting email"+ e.getMessage());
        }
        return airConditionId;
    }

    public void deleteAirCondition(String id) {
        databaseReference.child(id).removeValueAsync();
        FirebaseDatabase.getInstance().getReference("airCondition_users").child(id).removeValueAsync();
        System.out.println("Air condition deleted");
    }

    public void turnOnAirCondition(String id) throws ExecutionException, InterruptedException {
        checkAccess(id);
        databaseReference.child(id).child("isWorking").setValueAsync(true);
        System.out.println("Air condition turned on");
        logAcEvent(id,"Air condition turned on");

    }

    public void turnOffAirCondition(String id) throws ExecutionException, InterruptedException {
        checkAccess(id);
        databaseReference.child(id).child("isWorking").setValueAsync(false);
        System.out.println("Air condition turned off");
        logAcEvent(id,"Air condition turned off");

    }

    public void grantAccess(String id, String uid, String targetEmail) throws ExecutionException, InterruptedException, TimeoutException {
//        AirCondition airCondition = getAirConditionById(id);
//        if(!airCondition.getOwnerUid().equals(uid)) {
//            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Only admin can grant access");
//        }
        String safeEmail = Base64.getUrlEncoder().encodeToString(targetEmail.getBytes(StandardCharsets.UTF_8));
        FirebaseDatabase.getInstance().getReference("airCondition_users").child(id).child(safeEmail).setValueAsync(true);
    }

    public AirCondition getAirConditionById(String airConditionId) throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<AirCondition> future = new CompletableFuture<>();
        databaseReference.child(airConditionId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                AirCondition airCondition = snapshot.getValue(AirCondition.class);
                if (airCondition != null) {
                    airCondition.setId(snapshot.getKey());
                    future.complete(airCondition);
                }
                else {
                    future.completeExceptionally(new RuntimeException("Air condition not found"));
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                future.completeExceptionally(new RuntimeException("Firebase read failed"));
            }
        });
        return future.get();
    }


    private void checkAccess (String airConditionId) throws ExecutionException, InterruptedException {
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
        DatabaseReference airConditionUsersRef = FirebaseDatabase.getInstance().getReference("airCondition_users").child(airConditionId).child(safeEmail);

        airConditionUsersRef.addListenerForSingleValueEvent(new ValueEventListener() {
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

    public void revokeAC(String id, String uid, String targetEmail) throws ExecutionException, InterruptedException, TimeoutException {
//        AirCondition airCondition = getAirConditionById(id);
//        if(!airCondition.getOwnerUid().equals(uid)) {
//            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Only owner can revoke access");
//        }
        String safeEmail = Base64.getUrlEncoder().encodeToString(targetEmail.getBytes(StandardCharsets.UTF_8));
        FirebaseDatabase.getInstance().getReference("airCondition_users").child(id).child(safeEmail).removeValueAsync();
    }

    public void setDesiredTemperature(String id, double value) throws ExecutionException, InterruptedException {
        checkAccess(id);
        databaseReference.child(id).child("desiredTemperature").setValueAsync(value);
        logAcEvent(id,"DesiredTemperature set to "+value);

    }

    public void setMode(String id, String value) throws ExecutionException, InterruptedException {
        checkAccess(id);
        databaseReference.child(id).child("mode").setValueAsync(value);
        logAcEvent(id,"Mode set to " + value);
    }

    private void logAcEvent(String acId, String message) {
        DatabaseReference logRef = databaseReference.child(acId).child("logs").push();
        Map<String, Object> logData = new HashMap<>();
        logData.put("message", message);
        logData.put("timestamp", System.currentTimeMillis());
        logRef.setValueAsync(logData);
    }

}
