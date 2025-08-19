package com.example.test.Model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
public class Door
{
    private String id;
    private Boolean locked;
    private Boolean open;
    private long lastPing;
    private String ownerUid;
    private String name;
    private String client;
}


