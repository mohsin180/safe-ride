package com.saferide.monolith.rides.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A co-passenger's request to join a ride, pending the host's approval.
 * Carries the requester's own pickup/destination so the host can judge
 * whether their route fits before accepting. On accept the requester
 * becomes a {@link RideParticipants}; on decline the request is closed.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "join_request", indexes = {
        @Index(name = "idx_join_request_ride", columnList = "ride_id")
})
public class JoinRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    // The requester's own route (where THEY want to go), shown to the host.
    private String pickup;
    private Double pickupLat;
    private Double pickupLng;
    private String drop;
    private Double dropLat;
    private Double dropLng;

    /** How many seats the requester wants (1..seats-available). Defaults to 1
     *  when the client doesn't specify. */
    private Integer seats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JoinRequestStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
