package com.saferide.monolith.rides.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One rider's fare on a ride, settled in cash. Created (PENDING) when the ride
 * completes — one row per rider (host + each co-passenger) for their share of
 * the fare — and flipped to COLLECTED when the driver confirms they took the
 * cash. This is the payment ledger: no gateway yet, but every fare owed and
 * collected is recorded so earnings and receipts are real.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ride_payment", uniqueConstraints = @UniqueConstraint(
        name = "uk_ride_payment_ride_user", columnNames = {"ride_id", "user_id"}),
        indexes = {
        @Index(name = "idx_ride_payment_ride", columnList = "ride_id"),
        @Index(name = "idx_ride_payment_user", columnList = "user_id")
})
public class RidePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    /** The rider who owes this fare. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private double amount;

    @Column(nullable = false)
    private String currency;

    /** Settlement method — CASH for now (no gateway yet). */
    @Column(nullable = false)
    private String method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    /** When the driver confirmed collection; null while PENDING. */
    @Column(name = "collected_at")
    private Instant collectedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
