package com.example.simulator.simulator;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class AirConditionSimulator implements CommandLineRunner {

    private final DatabaseReference airRef;
    private final Set<String> airIds = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    public AirConditionSimulator(FirebaseApp firebaseApp) {
        this.airRef = FirebaseDatabase.getInstance(firebaseApp).getReference("airCondition");
    }

    @Override
    public void run(String... args) {
        System.out.println("Starting ac simulator...");

        // Ping all ac every 3 seconds
        scheduler.scheduleAtFixedRate(() -> {
            long timestamp = System.currentTimeMillis();
            synchronized (airIds) {
                for (String id : airIds) {
                    airRef.child(id).child("lastPing").setValueAsync(timestamp);
                    System.out.println("Pinging ac: " + id + " at " + timestamp);
                }
            }
        }, 0, 3, TimeUnit.SECONDS);


        airRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                String id = snapshot.getKey();
                if (id != null && !airIds.contains(id)) {
                    synchronized (airIds) {
                        airIds.add(id);
                    }
                    System.out.println("Added air conditioner to simulation: " + id);
                }
            }

            @Override
            public void onChildRemoved(DataSnapshot snapshot) {
                String id = snapshot.getKey();
                if (id != null) {
                    synchronized (airIds) {
                        airIds.remove(id);
                    }
                    System.out.println("Removed air conditioner from simulation: " + id);
                }
            }

            @Override public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}
            @Override public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
            @Override public void onCancelled(DatabaseError error) {
                System.err.println("Listener cancelled: " + error.getMessage());
            }
        });

        scheduler.scheduleAtFixedRate(() -> {
            synchronized (airIds) {
                for (String id : airIds) {
                    adjustTemperature(id);
                }
            }
        },0,10, TimeUnit.SECONDS);

    }

    private void adjustTemperature(String acId) {
        airRef.child(acId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Boolean isWorking = dataSnapshot.child("isWorking").getValue(Boolean.class);
                String mode = dataSnapshot.child("mode").getValue(String.class);
                Double currentTemperature = dataSnapshot.child("currentTemperature").getValue(Double.class);
                Double desiredTemperature = dataSnapshot.child("desiredTemperature").getValue(Double.class);

                if (Boolean.TRUE.equals(isWorking) && mode != null &&  currentTemperature != null && desiredTemperature != null) {
                    double updatedTemperature = currentTemperature;

                    if(mode.equalsIgnoreCase("heat") && currentTemperature < desiredTemperature) {
//                    if(currentTemperature < desiredTemperature) {
                        updatedTemperature += 0.1;
                    } else if(mode.equalsIgnoreCase("cold") && currentTemperature > desiredTemperature) {
//                    } else if(currentTemperature > desiredTemperature) {
                        updatedTemperature -= 0.1;
                    }
                    updatedTemperature = Math.round(updatedTemperature * 10.0) / 10.0;

                    if (updatedTemperature != currentTemperature) {
                        airRef.child(acId).child("currentTemperature").setValueAsync(updatedTemperature);
                        //logAcEvent(acId,"temperature adjusted to " + updatedTemperature + "°C");
                        System.out.println("Adjusted temperature to " + updatedTemperature + "°C");
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                System.err.println("Listener cancelled: " + databaseError.getMessage());
            }
        });
    }

    private void logAcEvent(String acId, String message) {
        DatabaseReference logRef = airRef.child(acId).child("logs").push();
        Map<String, Object> logData = new HashMap<>();
        logData.put("message", message);
        logData.put("timestamp", System.currentTimeMillis());
        logRef.setValueAsync(logData);
    }
}
