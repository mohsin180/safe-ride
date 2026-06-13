package com.safe_ride.rides_service.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One rating given on a completed ride — a passenger rating the driver, or
 * the driver rating one of the passengers. The unique constraint enforces
 * "one rating per (ride, rater, rated)" so nobody can rate the same person
 * twice for the same trip.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "rating",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rating_ride_rater_rated",
                columnNames = {"ride_id", "rater_id", "rated_id"})
)
public class Rating {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    @Column(name = "rater_id", nullable = false)
    private UUID raterId;

    @Column(name = "rated_id", nullable = false)
    private UUID ratedId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RatedRole ratedRole;

    /** 1–5 stars. */
    @Column(nullable = false)
    private int stars;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
