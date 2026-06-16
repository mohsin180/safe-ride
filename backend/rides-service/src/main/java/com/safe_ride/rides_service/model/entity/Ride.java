package com.safe_ride.rides_service.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ride {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID createdByUserId;
    private UUID driverId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;
    @Embedded
    @AttributeOverrides(
            {
                    @AttributeOverride(name = "address", column = @Column(name = "pickup_address")),
                    @AttributeOverride(name = "latitude", column = @Column(name = "pickup_lat")),
                    @AttributeOverride(name = "longitude", column = @Column(name = "pickup_long")),
            }
    )
    private Location pickup;
    @Embedded
    @AttributeOverrides(
            {
                    @AttributeOverride(name = "address", column = @Column(name = "dest_address")),
                    @AttributeOverride(name = "latitude", column = @Column(name = "dest_lat")),
                    @AttributeOverride(name = "longitude", column = @Column(name = "dest_long")),
            }
    )
    private Location destination;
    @Column(nullable = false)
    private int totalSeats;
    @Column(nullable = false)
    private int availableSeats;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RideType rideType;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "ride", orphanRemoval = true)
    private List<RideParticipants> participants = new ArrayList<>();
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RideStatus status;
    /** Road (driving) distance of the trip in km, resolved from the routing
     *  API at creation time. Falls back to the Haversine straight-line distance
     *  when routing is unavailable. Null only for legacy rides created before
     *  this column existed. */
    private Double routeDistanceKm;
    /** Estimated driving duration of the trip in minutes, from the routing API
     *  at creation time. Falls back to a distance/30 km/h estimate when routing
     *  is unavailable. Null only for legacy rides. */
    private Integer routeDurationMin;
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    private Instant cancelledAt;
    /** Stamped when the driver completes the ride. Null for rides that
     *  completed before this column existed and for non-completed rides. */
    private Instant completedAt;
    @Version
    private Long version;
}