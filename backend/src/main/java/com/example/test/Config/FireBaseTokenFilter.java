package com.example.test.Config;

import com.example.test.Model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.database.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

@Component
public class FireBaseTokenFilter extends OncePerRequestFilter {
    private final FirebaseAuth firebaseAuth;
    private final FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
    private static final String ROLE_USER = "ROLE_USER";

    public FireBaseTokenFilter(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }
    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain chain)
            throws ServletException, IOException {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                FirebaseToken firebaseToken = null;
                try {
                    firebaseToken = firebaseAuth.verifyIdToken(token, true);
                } catch (FirebaseAuthException e) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Firebase token" + e.getMessage());
                }
                String uid = String.valueOf(firebaseToken.getClaims().get("user_id"));
                String email = String.valueOf(firebaseToken.getEmail());
                if (uid == null) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Firebase token");
                }
                String safeEmail = Base64.getUrlEncoder().encodeToString(email.getBytes(StandardCharsets.UTF_8));
                DatabaseReference databaseReference = firebaseDatabase.getReference("user").child(safeEmail);

                CompletableFuture<String> roleFuture = new CompletableFuture<>();

                databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        User user = dataSnapshot.getValue(User.class);
                        if (user != null && user.getRole() != null) {
                            roleFuture.complete(user.getRole());
                        } else {
                            roleFuture.complete(ROLE_USER);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        roleFuture.completeExceptionally(new RuntimeException("Database error: " + databaseError.getMessage()));
                    }
                });

                try {
                    String role = roleFuture.get();
                    List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(role));
                    var authentication = new UsernamePasswordAuthenticationToken(uid, firebaseToken, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            else {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Missing or invalid Authorization header");
            }
            chain.doFilter(request,response);
            }
    }