package com.example.simulator.simulator;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;

@Component
public class DoorSimulator implements CommandLineRunner {

    private final DatabaseReference doorRef;
    private final Set<String> doorIds = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final Random random = new Random();

    public DoorSimulator(FirebaseApp firebaseApp) {
        this.doorRef = FirebaseDatabase.getInstance(firebaseApp).getReference("door");
    }

    @Override
    public void run(String... args) {
        System.out.println("Starting door simulator...");

        // pinging all doors every 3 seconds
        scheduler.scheduleAtFixedRate(() -> {
            long timestamp = System.currentTimeMillis();
            synchronized (doorIds) {
                for (String id : doorIds) {
                    doorRef.child(id).child("lastPing").setValueAsync(timestamp);
                    System.out.println("Pinging door: " + id + " at " + timestamp);
                }
            }
        }, 0, 3, TimeUnit.SECONDS);

        //ako su neka vrata bila otvorena prije pocetka simulacije
        doorRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot doorSnapshot : snapshot.getChildren()) {
                    String id = doorSnapshot.getKey();
                    if (id != null) {
                        Boolean open = doorSnapshot.child("open").getValue(Boolean.class);
                        Boolean locked = doorSnapshot.child("locked").getValue(Boolean.class);
                        synchronized (doorIds) {
                            doorIds.add(id);
                        }
                        if (Boolean.TRUE.equals(open) && Boolean.FALSE.equals(locked)) {
                            long openDuration = 5 + random.nextInt(40); // how long it stays open
                            long effectiveDuration = Math.min(openDuration, 30); // max 30s

                            scheduler.schedule(() -> closeAndLockDoor(id, effectiveDuration), effectiveDuration, TimeUnit.SECONDS);
                        }
                        //scheduleRandomOpen(id);
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                System.err.println("Failed to fetch initial doors: " + error.getMessage());
            }
        });
//zadnje dodano

        doorRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                String id = snapshot.getKey();
                if (id != null && !doorIds.contains(id)) {
                    synchronized (doorIds) {
                        doorIds.add(id);
                    }
                    System.out.println("Added new door to simulation: " + id);
                    scheduleRandomOpen(id);
                }
            }

            @Override
            public void onChildRemoved(DataSnapshot snapshot) {
                String id = snapshot.getKey();
                if (id != null) {
                    synchronized (doorIds) {
                        doorIds.remove(id);
                    }
                    System.out.println("Door removed from simulation: " + id);
                }
            }
            @Override public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}
            @Override public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
            @Override public void onCancelled(DatabaseError error) {
                System.err.println("Listener cancelled: " + error.getMessage());
            }
        });
    }

    // Schedules random open/close simulation for a single door
    private void scheduleRandomOpen(String doorId) {
        long delay = 5 + random.nextInt(30);
        scheduler.schedule(() -> tryOpenDoor(doorId), delay, TimeUnit.SECONDS);
    }

    // Tries to open a door if it's unlocked
    private void tryOpenDoor(String doorId) {
        doorRef.child(doorId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Boolean locked = snapshot.child("locked").getValue(Boolean.class);
                Boolean open = snapshot.child("open").getValue(Boolean.class);
                if (Boolean.FALSE.equals(locked) && Boolean.FALSE.equals(open)) {
                    doorRef.child(doorId).child("open").setValueAsync(true);
                    logDoorEvent(doorId, "Door opened");
                    System.out.println("Door opened: " + doorId);

                    long openDuration = 5 + random.nextInt(40); // how long it stays open -> 5–45 seconds
                    long effectiveDuration = Math.min(openDuration, 30); // automatically close and lock after the 30s of being open

                    scheduler.schedule(() -> closeAndLockDoor(doorId,effectiveDuration), effectiveDuration, TimeUnit.SECONDS);
                }

                // Schedule the next random attempt regardless of whether it opened
                scheduleRandomOpen(doorId);
            }
            @Override
            public void onCancelled(DatabaseError error) {
                System.err.println("Failed to read door: " + error.getMessage());
                scheduleRandomOpen(doorId);
            }
        });
    }

    // Closes and locks the door
    private void closeAndLockDoor(String doorId, long effectiveDuration) {
        doorRef.child(doorId).child("open").setValueAsync(false);
        doorRef.child(doorId).child("locked").setValueAsync(true);
        String message = ("Door closed and locked after "+effectiveDuration+" seconds");
        logDoorEvent(doorId,message);
        System.out.println("Door closed and locked: " + doorId +" after "+effectiveDuration+" seconds");
    }

    private void logDoorEvent(String doorId, String message) {
        DatabaseReference logRef = doorRef.child(doorId).child("logs").push();
        Map<String, Object> logData = new HashMap<>();
        logData.put("message", message);
        logData.put("timestamp", System.currentTimeMillis());
        logRef.setValueAsync(logData);
    }
}