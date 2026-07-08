package com.safe_ride.rides_service.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A record of one ride cancellation by a rider. Kept even after the ride is
 * gone so we can (a) surface a per-user strike count for repeat cancellers and
 * (b) settle the recorded {@code fee} once payments exist. A zero fee means
 * the cancellation was within the free window (before the driver arrived).
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ride_cancellation", indexes = {
        @Index(name = "idx_ride_cancellation_user", columnList = "user_id")
})
public class RideCancellation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    /** The rider who cancelled. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Flat fee recorded (0 when cancelled inside the free window). */
    @Column(nullable = false)
    private double fee;

    @Column(nullable = false)
    private String currency;

    /** Optional free-text reason the rider gave. */
    @Column(length = 300)
    private String reason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
