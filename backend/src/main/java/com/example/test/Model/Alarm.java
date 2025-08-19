package com.example.test.Model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
public class Alarm {
    private String id;
    private Boolean isWorking;
    private Boolean breakIn;
    private String ownerUid;
    private String name;
    private String client;
    private long lastPing;
}
