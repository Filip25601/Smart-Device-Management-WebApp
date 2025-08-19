package com.example.test.Controller;

import com.example.test.Service.AlarmService;
import com.google.firebase.auth.FirebaseAuthException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/alarms")
public class AlarmController {
    private final AlarmService alarmService;
    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    //new
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<String> addAlarm(@RequestParam String name) throws FirebaseAuthException {
        String uid = SecurityContextHolder.getContext().getAuthentication().getName();
        String alarmId = alarmService.createAlarm(uid,name);
        return ResponseEntity.ok("Alarm created " + alarmId);
    }

    //Delete
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteAlarm(@PathVariable String id) {
        alarmService.deleteAlarm(id);
        return ResponseEntity.ok("Alarm deleted " + id);
    }

    //turn on
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @PostMapping("/{id}/on")
    public ResponseEntity<String> turnOnAlarm(@PathVariable String id) throws ExecutionException, InterruptedException {
        alarmService.turnOnAlarm(id);
        return ResponseEntity.ok("Alarm turned on " + id);
    }

    //turn off
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @PostMapping("/{id}/off")
    public ResponseEntity<String> turnOffAlarm(@PathVariable String id) throws ExecutionException, InterruptedException {
        alarmService.turnOffAlarm(id);
        return ResponseEntity.ok("Alarm turned off " + id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/grant")
    public ResponseEntity<String> grantAlarm(@PathVariable String id,@RequestParam String targetEmail) {
        String uid = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            alarmService.grantAccess(id,uid,targetEmail);
            return ResponseEntity.ok("Granted " + targetEmail);
        } catch (Exception e){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"User not granted access");
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}/revoke")
    public ResponseEntity<String> revokeAlarm(@PathVariable String id,@RequestParam String targetEmail) {
        String uid = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            alarmService.revokeAlarm(id,uid,targetEmail);
            return ResponseEntity.ok("Revoked access for " + targetEmail);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Could not revoke access");
        }
    }

    //turn off
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @PostMapping("/{id}/endBreakIn")
    public ResponseEntity<String> endBreakIn(@PathVariable String id) throws ExecutionException, InterruptedException {
        alarmService.endBreakIn(id);
        return ResponseEntity.ok("Alarm breakIn turned off " + id);
    }


}
