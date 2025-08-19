package com.example.simulator.simulator;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

@Component
public class AlarmSimulator implements CommandLineRunner {

    private final DatabaseReference alarmRef;
    private final Set<String> alarmIds = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final Random random = new Random();

    public AlarmSimulator(FirebaseApp firebaseApp) {
        this.alarmRef = FirebaseDatabase.getInstance(firebaseApp).getReference("alarm");
    }

    @Override
    public void run(String... args) {
        System.out.println("Starting alarm simulator...");

        // Ping all alarms every 3 seconds
        scheduler.scheduleAtFixedRate(() -> {
            long timestamp = System.currentTimeMillis();
            synchronized (alarmIds) {
                for (String id : alarmIds) {
                    alarmRef.child(id).child("lastPing").setValueAsync(timestamp);
                    System.out.println("Pinging alarm: " + id + " at " + timestamp);
                }
            }
        }, 0, 3, TimeUnit.SECONDS);

        // Listen for new alarms
        alarmRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                String id = snapshot.getKey();
                if (id != null && !alarmIds.contains(id)) {
                    synchronized (alarmIds) {
                        alarmIds.add(id);
                    }
                    System.out.println("Added alarm to simulation: " + id);
                    scheduleRandomBreakIn(id);
                }
            }

            @Override
            public void onChildRemoved(DataSnapshot snapshot) {
                String id = snapshot.getKey();
                if (id != null) {
                    synchronized (alarmIds) {
                        alarmIds.remove(id);
                    }
                    System.out.println("Removed alarm from simulation: " + id);
                }
            }

            @Override public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}
            @Override public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
            @Override public void onCancelled(DatabaseError error) {
                System.err.println("Listener cancelled: " + error.getMessage());
            }
        });
    }

    private void scheduleRandomBreakIn(String alarmId) {
        int delay = 10 + random.nextInt(101); // Random between 10 and 110
        scheduler.schedule(() -> triggerBreakIn(alarmId), delay, TimeUnit.SECONDS);
    }

    private void triggerBreakIn(String alarmId) {
        alarmRef.child(alarmId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Boolean isWorking = snapshot.child("isWorking").getValue(Boolean.class);
                Boolean breakIn = snapshot.child("breakIn").getValue(Boolean.class);

                LocalDateTime myDate = LocalDateTime.now();
                DateTimeFormatter myFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
                String formattedDate = myDate.format(myFormat);

                String message = "Break-in simulated ";
                if (Boolean.TRUE.equals(isWorking) && Boolean.FALSE.equals(breakIn)) {
                    System.out.println("Break-in simulated at alarm: " + alarmId + " at "+ formattedDate);
                    alarmRef.child(alarmId).child("breakIn").setValueAsync(true);
                    logAlarmEvent(alarmId,message);

                }
                // schedule next break-in
                scheduleRandomBreakIn(alarmId);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                System.err.println("Failed to check isWorking for alarm: " + alarmId);
                // schedule the next attempt
                scheduleRandomBreakIn(alarmId);
            }
        });
    }

    private void logAlarmEvent(String alarmId, String message) {
        DatabaseReference logRef = alarmRef.child(alarmId).child("logs").push();
        Map<String, Object> logData = new HashMap<>();
        logData.put("message", message);
        logData.put("timestamp", System.currentTimeMillis());
        logRef.setValueAsync(logData);
    }
}
