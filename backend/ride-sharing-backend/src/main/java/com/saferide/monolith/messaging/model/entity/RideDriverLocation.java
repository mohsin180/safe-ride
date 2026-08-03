package com.saferide.monolith.messaging.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * The last-known position of a ride's driver. One row per ride (keyed by
 * {@code rideId}), upserted every time the driver relays a position. Lets a
 * rider who opens the app late — or whose driver's app was briefly off the
 * live-track topic — see the car's last spot immediately, instead of an
 * empty map until the next broadcast. Ephemeral by nature: overwritten on
 * every update and irrelevant once the trip ends.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ride_driver_location")
public class RideDriverLocation {

    /** The ride this position belongs to — the natural key (one row per ride). */
    @Id
    @Column(name = "ride_id")
    private UUID rideId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    /** Heading in degrees, if the device reported one. */
    private Double bearing;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
