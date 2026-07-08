package com.safe_ride.rides_service.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-rider trip progress (host + each co-passenger): WAITING → PICKED →
 * DROPPED. Persisted so the driver's cockpit survives an app restart mid-trip
 * and every rider's own screen can reflect their real state, instead of the
 * old local-only tracking. One row per (ride, rider).
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ride_rider_progress", uniqueConstraints = @UniqueConstraint(
        name = "uk_ride_rider_progress", columnNames = {"ride_id", "user_id"}),
        indexes = @Index(name = "idx_ride_rider_progress_ride", columnList = "ride_id"))
public class RideRiderProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiderProgressStatus status;

    @Column(name = "picked_at")
    private Instant pickedAt;

    @Column(name = "dropped_at")
    private Instant droppedAt;
}
