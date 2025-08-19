package com.example.test.Controller;

import com.example.test.Service.AirConditionService;
import com.google.firebase.auth.FirebaseAuthException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/airConditions")
public class AirConditionController {
    private final AirConditionService airConditionService;

    public AirConditionController(AirConditionService airConditionService) {
        this.airConditionService = airConditionService;
    }
    //new
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<String> addAirCondition(@RequestParam String name) throws FirebaseAuthException {
        String uid = SecurityContextHolder.getContext().getAuthentication().getName();
        String airConditionId = airConditionService.createAirCondition(uid,name);
        return ResponseEntity.ok("AirCondition created " + airConditionId);
    }

    //Delete
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteAirCondition(@PathVariable String id) {
        airConditionService.deleteAirCondition(id);
        return ResponseEntity.ok("AirCondition deleted " + id);
    }

    //turn on
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @PostMapping("/{id}/on")
    public ResponseEntity<String> turnOnAirCondition(@PathVariable String id) throws ExecutionException, InterruptedException {
        airConditionService.turnOnAirCondition(id);
        return ResponseEntity.ok("AirCondition turned on " + id);
    }

    //turn off
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @PostMapping("/{id}/off")
    public ResponseEntity<String> turnOffAirCondition(@PathVariable String id) throws ExecutionException, InterruptedException {
        airConditionService.turnOffAirCondition(id);
        return ResponseEntity.ok("AirCondition turned off " + id);
    }

    //grant
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/grant")
    public ResponseEntity<String> grantAirCondition(@PathVariable String id,@RequestParam String targetEmail) {
        String uid = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            airConditionService.grantAccess(id,uid,targetEmail);
            return ResponseEntity.ok("Granted " + targetEmail);
        } catch (Exception e){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"User not granted access");
        }
    }

    //revoke
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}/revoke")
    public ResponseEntity<String> revokeAirCondition(@PathVariable String id,@RequestParam String targetEmail) {
        String uid = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            airConditionService.revokeAC(id,uid,targetEmail);
            return ResponseEntity.ok("Revoked access for " + targetEmail);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Could not revoke access");
        }
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @PostMapping("/{id}/temperature")
    public ResponseEntity<String> setTemperature(@PathVariable String id,@RequestParam double value) throws ExecutionException, InterruptedException {
        airConditionService.setDesiredTemperature(id,value);
        return ResponseEntity.ok("Temperature set to " + value);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @PostMapping("/{id}/mode")
    public ResponseEntity<String> setMode(@PathVariable String id,@RequestParam String value) throws ExecutionException, InterruptedException {
        if(!value.equals("cold") && !value.equals("heat")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid mode");
        }
        airConditionService.setMode(id,value);
        return ResponseEntity.ok("Mode set to " + value);
    }


}