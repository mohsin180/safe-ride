package com.saferide.monolith.messaging.repo;

import com.saferide.monolith.messaging.model.entity.RideDriverLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Last-known driver position per ride. Keyed by rideId (one row per ride). */
public interface RideDriverLocationRepository
        extends JpaRepository<RideDriverLocation, UUID> {
}
