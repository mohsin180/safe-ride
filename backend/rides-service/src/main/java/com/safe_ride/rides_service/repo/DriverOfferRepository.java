package com.safe_ride.rides_service.repo;

import com.safe_ride.rides_service.model.entity.DriverOffer;
import com.safe_ride.rides_service.model.entity.DriverOfferStatus;
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
