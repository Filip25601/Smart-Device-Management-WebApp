package com.example.test.Controller;

import com.example.test.Service.DoorService;
import com.google.firebase.auth.FirebaseAuthException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/doors")
public class DoorController {
    private final DoorService doorService;
    public DoorController(DoorService doorService) {
        this.doorService = doorService;
    }

    //Create a door
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<String> createDoor(@RequestParam String name) throws FirebaseAuthException {
        String uid = SecurityContextHolder.getContext().getAuthentication().getName();
        String doorId = doorService.createDoor(uid,name);
        return ResponseEntity.ok("Door created " + doorId);
    }

    //Delete
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteDoor(@PathVariable String id) {
        doorService.deleteDoor(id);
        return ResponseEntity.ok("Door deleted" + id);
    }

    //Open door
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @PostMapping("/{id}/open")
    public ResponseEntity<String> openDoor(@PathVariable String id) {
        doorService.openDoor(id);
        return ResponseEntity.ok("Door"+ id +"opened");
    }

    //Close the door
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @PostMapping("/{id}/close")
    public ResponseEntity<String> closeDoor(@PathVariable String id) {
        doorService.closeDoor(id);
        return ResponseEntity.ok("Door"+ id +"closed");
    }

    //Lock the door
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @PostMapping("/{id}/lock")
    public ResponseEntity<String> lockDoor(@PathVariable String id) throws ExecutionException, InterruptedException {
        doorService.lockDoor(id);
        return ResponseEntity.ok("Door"+ id +"locked");
    }

    //Unlock a door
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @PostMapping("/{id}/unlock")
    public ResponseEntity<String> unlockDoor(@PathVariable String id ) throws ExecutionException, InterruptedException {
        doorService.unlockDoor(id);
        return ResponseEntity.ok("Door"+ id +"unlocked");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/grant")
    public ResponseEntity<String> grantAccess(@PathVariable String id,@RequestParam String targetEmail) {
        String uid = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            doorService.grantAccess(id,uid,targetEmail);
            return ResponseEntity.ok("Granted access " + targetEmail);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"User not found in database");
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}/revoke")
    public ResponseEntity<String> revokeAccess(@PathVariable String id,@RequestParam String targetEmail) {
        String uid = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            doorService.revokeAccess(id,uid,targetEmail);
            return ResponseEntity.ok("Revoked access for " + targetEmail);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Could not revoke access");
        }
    }

}
