package com.example.test.Service;

import com.example.test.Model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.database.*;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Service
public class UserService {
    private final DatabaseReference databaseReference;
    private final FirebaseAuth firebaseAuth;
    private static final String ROLE_USER = "ROLE_USER";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";
    public UserService(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
        this.databaseReference = FirebaseDatabase.getInstance().getReference("user");
    }

    public void createUser(FirebaseToken token) {
        String uid = token.getUid();
        String email = token.getEmail();
        String displayName = token.getName();
        String safeEmail = Base64.getUrlEncoder().encodeToString(email.getBytes(StandardCharsets.UTF_8));

        databaseReference.child(safeEmail).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                User existingUser = snapshot.getValue(User.class);
                User user = new User();
                user.setId(uid);
                user.setEmail(email);
                user.setDisplayName(displayName);

                if (existingUser != null) {
                    user.setRole(existingUser.getRole() != null ? existingUser.getRole() : ROLE_USER );
                    user.setClient(existingUser.getClient());

                    if (existingUser.getClient() == null || existingUser.getClient().isEmpty()) {
                        lookupClientAndSaveUser(safeEmail,user);
                    } else {
                        databaseReference.child(safeEmail).setValueAsync(user);
                    }
                }else {
                    user.setRole(ROLE_USER);
                    lookupClientAndSaveUser(safeEmail,user);
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                System.err.println("Listener cancelled: " + error.getMessage());
            }
        });
    }

    private void lookupClientAndSaveUser(String safeEmail, User user) {
        FirebaseDatabase.getInstance().getReference("client").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot clientSnapshot : snapshot.getChildren()) {
                    if (clientSnapshot.hasChild(safeEmail)) {
                        user.setClient(clientSnapshot.getKey());
                        break;
                    }
                }
                databaseReference.child(safeEmail).setValueAsync(user);
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                System.err.println("Client lookup failed" + databaseError.getMessage());
                user.setClient("");
                databaseReference.child(safeEmail).setValueAsync(user);
            }
        });

    }

    public void deleteUser(String email) throws FirebaseAuthException {
        String safeEmail = Base64.getUrlEncoder().encodeToString(email.getBytes(StandardCharsets.UTF_8));

        databaseReference.child(safeEmail).removeValueAsync();
        try {
            UserRecord userRecord = firebaseAuth.getUserByEmail(email);
            if (userRecord != null) {
                firebaseAuth.deleteUser(userRecord.getUid());
            }
        } catch (FirebaseAuthException e) {
            System.err.println("Warning: user record could not be deleted "+e.getMessage());
        }

        DatabaseReference clientRef = FirebaseDatabase.getInstance().getReference("client");
        clientRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot clientSnapshot : snapshot.getChildren()) {
                    if (clientSnapshot.hasChild(safeEmail)) {
                        clientSnapshot.getRef().child(safeEmail).removeValueAsync();
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                System.err.println("Client lookup failed" + databaseError.getMessage());
            }
        });
        removeUserFromDevices("door_users", safeEmail);
        removeUserFromDevices("alarm_users", safeEmail);
        removeUserFromDevices("airCondition_users", safeEmail);
    }

    private void removeUserFromDevices(String node, String safeEmail){
        FirebaseDatabase.getInstance().getReference(node).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot deviceSnapshot : snapshot.getChildren()) {
                    if (deviceSnapshot.hasChild(safeEmail)) {
                        deviceSnapshot.getRef().child(safeEmail).removeValueAsync();
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                System.err.println("Client lookup failed" + databaseError.getMessage());
            }
        });
    }


    public void makeAdmin(String targetEmail, String client) {
        String safeEmail = Base64.getUrlEncoder().encodeToString(targetEmail.getBytes(StandardCharsets.UTF_8));
        FirebaseDatabase.getInstance().getReference("client").child(client).child(safeEmail).setValueAsync(true);

        databaseReference.child(safeEmail).child("email").setValueAsync(targetEmail);
        databaseReference.child(safeEmail).child("client").setValueAsync(client);
        databaseReference.child(safeEmail).child("role").setValueAsync(ROLE_ADMIN);

    }

    public List<User> getAllUsers() {
        CompletableFuture<List<User>> future = new CompletableFuture<>();
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<User> users = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    User user = child.getValue(User.class);
                    if (user!=null && !Objects.equals(user.getRole(), ROLE_SUPER_ADMIN)) {
                        users.add(user);
                    }
                }
                future.complete(users);
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                future.completeExceptionally(new RuntimeException("database error"+databaseError.getMessage()));
            }
        });
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get users",e);
        }
    }

    public void assignClient(String targetEmail, String client) {
        String safeEmail = Base64.getUrlEncoder().encodeToString(targetEmail.getBytes(StandardCharsets.UTF_8));
        FirebaseDatabase.getInstance().getReference("client").child(client).child(safeEmail).setValueAsync(true);
        databaseReference.child(safeEmail).child("client").setValueAsync(client);
        databaseReference.child(safeEmail).child("email").setValueAsync(targetEmail);
    }
}