package com.saferide.monolith.rides.repo;

import com.saferide.monolith.rides.model.entity.RideDeparture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RideDepartureRepository extends JpaRepository<RideDeparture, UUID> {

    boolean existsByRideIdAndUserId(UUID rideId, UUID userId);
}
