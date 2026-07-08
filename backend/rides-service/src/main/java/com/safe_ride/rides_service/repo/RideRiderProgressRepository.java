package com.safe_ride.rides_service.repo;

import com.safe_ride.rides_service.model.entity.RideRiderProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Per-rider WAITING/PICKED/DROPPED progress for a ride. */
public interface RideRiderProgressRepository
        extends JpaRepository<RideRiderProgress, UUID> {

    List<RideRiderProgress> findByRideId(UUID rideId);

    Optional<RideRiderProgress> findByRideIdAndUserId(UUID rideId, UUID userId);

    boolean existsByRideId(UUID rideId);
}
