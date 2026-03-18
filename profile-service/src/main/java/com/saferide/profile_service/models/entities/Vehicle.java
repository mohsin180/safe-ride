package com.saferide.profile_service.models.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String make;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false, unique = true)
    private String number;

    @Column(nullable = false)
    private String color;

    @Column(nullable = false)
    private int seats;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_profile_id", nullable = false, unique = true)
    private DriverProfile driverProfile;
}
