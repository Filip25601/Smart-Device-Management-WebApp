package com.example.test.Controller;

import com.example.test.Model.User;
import com.example.test.Service.UserService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {
    private final UserService userService;
    private final FirebaseAuth firebaseAuth;

    @Autowired
    public UserController(UserService userService, FirebaseAuth firebaseAuth) {
        this.userService = userService;
        this.firebaseAuth = firebaseAuth;
    }
    @PostMapping("/login")
    public ResponseEntity<String> createUser(@RequestHeader("Authorization") String header) throws FirebaseAuthException {
        String token = header.replace("Bearer ", "");
        FirebaseToken decodedToken = firebaseAuth.verifyIdToken(token);
        userService.createUser(decodedToken);
        return ResponseEntity.ok("User created");
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteUser(@RequestParam String email){
        try {
            userService.deleteUser(email);
            return ResponseEntity.ok("User deleted");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting user");
        }
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @GetMapping("/all")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/make-admin")
    public ResponseEntity<String> makeAdmin(@RequestParam String targetEmail, @RequestParam String client) {
        try {
            userService.makeAdmin(targetEmail, client);
            return ResponseEntity.ok("Admin created");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Error while create admin and client");
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/assign-client")
    public ResponseEntity<String> assignClient(@RequestParam String targetEmail, @RequestParam String client) {
        try {
            userService.assignClient(targetEmail,client);
            return ResponseEntity.ok("User assigned to client");
        } catch (Exception e){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Error while assigning client");
        }
    }

}
