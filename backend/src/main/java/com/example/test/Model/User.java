package com.example.test.Model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class User {
    private String id;
    private String email;
    private String displayName;
    private String role;
    private String client;
}
