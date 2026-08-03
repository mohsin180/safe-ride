package com.saferide.monolith.rides.repo;

import com.saferide.monolith.rides.model.entity.DriverDeclinedRide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Rides each driver has dismissed from their feed. */
public interface DriverDeclinedRideRepository
        extends JpaRepository<DriverDeclinedRide, UUID> {

    @Query("SELECT d.rideId FROM DriverDeclinedRide d WHERE d.driverId = :driverId")
    List<UUID> findRideIdsByDriverId(@Param("driverId") UUID driverId);

    boolean existsByDriverIdAndRideId(UUID driverId, UUID rideId);
}
