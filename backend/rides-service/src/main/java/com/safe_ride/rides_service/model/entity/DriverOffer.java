package com.safe_ride.rides_service.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A driver's offer to drive a ride, pending the host's approval. Mirrors
 * {@link JoinRequest} but for drivers: the driver offers, and the host
 * accepts (which assigns the driver and moves the ride to ACCEPTED) or
 * declines. A ride is never assigned a driver without the host's approval.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "driver_offer", indexes = {
        @Index(name = "idx_driver_offer_ride", columnList = "ride_id")
})
public class DriverOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DriverOfferStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
