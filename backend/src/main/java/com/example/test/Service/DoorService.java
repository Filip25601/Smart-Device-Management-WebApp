package com.example.test.Service;

import com.example.test.Model.Door;
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
public class DoorService {
    private final DatabaseReference databaseReference;

    public DoorService() {
        this.databaseReference = FirebaseDatabase.getInstance().getReference("door");
    }

    public String createDoor(String uid, String name) throws FirebaseAuthException {
        String doorId = databaseReference.push().getKey();
        if (doorId == null) {
            throw new RuntimeException("Error creating door");
        }
        Door door = new Door();
        door.setId(doorId);
        door.setOpen(false);
        door.setLocked(true);
        door.setLastPing(System.currentTimeMillis());
        door.setOwnerUid(uid);
        door.setName(name);

        FirebaseDatabase.getInstance().getReference("user")
                .child(Base64.getUrlEncoder().encodeToString(FirebaseAuth.getInstance().getUser(uid).getEmail().getBytes(StandardCharsets.UTF_8)))
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        User user = snapshot.getValue(User.class);
                        if (user != null && user.getClient() != null) {
                            door.setClient(user.getClient());
                        }
                        databaseReference.child(doorId).setValueAsync(door);
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        System.err.println("Client fetch failed");
                    }
                });

        databaseReference.child(doorId).setValueAsync(door);

        try {
            ApiFuture<UserRecord> future = FirebaseAuth.getInstance().getUserAsync(uid);
            UserRecord userRecord = future.get();
            String email = userRecord.getEmail();
            if (email != null) {
                String safeEmail = Base64.getUrlEncoder().encodeToString(email.getBytes(StandardCharsets.UTF_8));
                FirebaseDatabase.getInstance().getReference("door_users")
                        .child(doorId)
                        .child(safeEmail)
                        .setValueAsync(true);
            }
        } catch (Exception e) {
            System.err.println("Error fetching user email: " + e.getMessage());
        }
        return doorId;
    }


    public void deleteDoor(String id) {
        databaseReference.child(id).removeValueAsync();
        FirebaseDatabase.getInstance().getReference("door_users").child(id).removeValueAsync();
        System.out.println("Door deleted");
    }

    public void openDoor(String id) {
        databaseReference.child(id).child("open").setValueAsync(true);
        logDoorEvent(id,"Opening door");
        System.out.println("Door opened.");
    }
    public void closeDoor(String id) {
        databaseReference.child(id).child("open").setValueAsync(false);
        //logDoorEvent(id,"Closing door");
        System.out.println("Door closed.");
    }

    public void lockDoor(String id) throws ExecutionException, InterruptedException {
        checkAccess(id);
        databaseReference.child(id).child("locked").setValueAsync(true);
        String message = "Door locked";
        logDoorEvent(id,message);
        System.out.println("Door locked.");
    }
    public void unlockDoor(String id) throws ExecutionException, InterruptedException {
        checkAccess(id);
        databaseReference.child(id).child("locked").setValueAsync(false);
        String message = "Door unlocked";
        logDoorEvent(id,message);
        System.out.println(message);
    }

    public void grantAccess(String id, String uid, String targetEmail) throws ExecutionException, InterruptedException, TimeoutException {
//        Door door = getDoorById(id);
//        if (!door.getOwnerUid().equals(uid)) {
//            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Only admin can grant access");
//        }
        String safeEmail = Base64.getUrlEncoder().encodeToString(targetEmail.getBytes(StandardCharsets.UTF_8));
        FirebaseDatabase.getInstance().getReference("door_users").child(id).child(safeEmail).setValueAsync(true);

    }

    public Door getDoorById(String doorId) throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Door> future = new CompletableFuture<>();
        databaseReference.child(doorId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Door door = snapshot.getValue(Door.class);
                if (door != null) {
                    door.setId(snapshot.getKey());
                    future.complete(door);
                }
                else {
                    future.completeExceptionally(new RuntimeException("Door not found"));
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                future.completeExceptionally(new RuntimeException("Firebase read failed"));
            }
        });
        return future.get();
    }

    private void checkAccess (String doorId) throws ExecutionException, InterruptedException {
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
        DatabaseReference doorUsersRef = FirebaseDatabase.getInstance().getReference("door_users").child(doorId).child(safeEmail);

        doorUsersRef.addListenerForSingleValueEvent(new ValueEventListener() {
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Access forbidden to the door");
        }
    }

    public void revokeAccess(String id, String uid, String targetEmail) throws ExecutionException, InterruptedException, TimeoutException {
//        Door door = getDoorById(id);
//        if (!door.getOwnerUid().equals(uid)) {
//            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Only owner can revoke access");
//        }
        String safeEmail = Base64.getUrlEncoder().encodeToString(targetEmail.getBytes(StandardCharsets.UTF_8));
        FirebaseDatabase.getInstance().getReference("door_users").child(id).child(safeEmail).removeValueAsync();
    }


    private void logDoorEvent(String doorId, String message) {
        DatabaseReference logRef = databaseReference.child(doorId).child("logs").push();
        Map<String, Object> logData = new HashMap<>();
        logData.put("message", message);
        logData.put("timestamp", System.currentTimeMillis());
        logRef.setValueAsync(logData);
    }
}
