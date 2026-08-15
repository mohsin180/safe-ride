package com.saferide.monolith.rides.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
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
    /** How many seats the HOST reserved for their own party (1..4). Stored
     *  separately from availableSeats (which also shrinks as co-passengers
     *  join) so the host always sees their real booking. @ColumnDefault
     *  backfills legacy rows to a single seat. */
    @Column(nullable = false)
    @ColumnDefault("1")
    private int hostSeats = 1;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RideType rideType;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "ride", orphanRemoval = true)
    private List<RideParticipants> participants = new ArrayList<>();
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RideStatus status;

    /** Whether the host has published this ride to the driver feed. Hosts
     *  gather co-passengers first, then publish; drivers only ever see
     *  published, still-PENDING rides. Defaults false at creation.
     *  {@code @ColumnDefault} so the auto-added column backfills existing
     *  rows (Postgres needs a default to add a NOT NULL column). */
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean publishedToDrivers = false;
    /** Road (driving) distance of the trip in km, resolved from the routing
     *  API at creation time. Falls back to the Haversine straight-line distance
     *  when routing is unavailable. Null only for legacy rides created before
     *  this column existed. */
    private Double routeDistanceKm;
    /** Estimated driving duration of the trip in minutes, from the routing API
     *  at creation time. Falls back to a distance/30 km/h estimate when routing
     *  is unavailable. Null only for legacy rides. */
    private Integer routeDurationMin;
    /** The HOST's own solo leg (pickup→drop) captured at creation, BEFORE any
     *  co-passenger detours reshape routeDistanceKm. Baseline for the pricing
     *  cap: the host never pays more than this leg's solo fare. Null = legacy
     *  ride (falls back to a Haversine estimate). */
    private Double hostLegKm;
    private Integer hostLegMin;
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