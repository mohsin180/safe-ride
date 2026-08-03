package com.saferide.monolith.rides.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A ride a driver dismissed from their feed. Persisted so a declined request
 * stays hidden for that driver instead of reappearing on the next poll. Other
 * drivers still see the ride — this is per-driver only.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "driver_declined_ride", uniqueConstraints = @UniqueConstraint(
        name = "uk_driver_declined_ride", columnNames = {"driver_id", "ride_id"}),
        indexes = @Index(name = "idx_driver_declined_driver", columnList = "driver_id"))
public class DriverDeclinedRide {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
