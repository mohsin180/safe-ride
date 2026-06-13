package com.safe_ride.rides_service.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Records that a passenger left a ride at their stop. Because the
 * {@code RideParticipants} row is removed on leave, this preserves the fact
 * that the user was a member — which authorizes the bidirectional
 * leave-time ratings (the leaver rating others, and remaining members
 * rating the leaver).
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ride_departure", uniqueConstraints = @UniqueConstraint(
        name = "uk_ride_departure_ride_user", columnNames = {"ride_id", "user_id"}))
public class RideDeparture {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant leftAt;
}
