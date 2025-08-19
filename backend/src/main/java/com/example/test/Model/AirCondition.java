package com.example.test.Model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
public class AirCondition   {
    private String id;
    private Boolean isWorking;
    private String ownerUid;
    private String name;
    private String client;
    private long lastPing;
    private String mode;
    private double currentTemperature;
    private double desiredTemperature;
}
