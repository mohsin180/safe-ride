package com.saferide.monolith.rides.repo;

import com.saferide.monolith.rides.model.entity.DriverOffer;
import com.saferide.monolith.rides.model.entity.DriverOfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DriverOfferRepository extends JpaRepository<DriverOffer, UUID> {

    boolean existsByRideIdAndDriverIdAndStatus(
            UUID rideId, UUID driverId, DriverOfferStatus status);

    List<DriverOffer> findByRideIdAndStatus(UUID rideId, DriverOfferStatus status);
}
