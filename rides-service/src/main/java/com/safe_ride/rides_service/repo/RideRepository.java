package com.safe_ride.rides_service.repo;

import com.safe_ride.rides_service.model.entity.Ride;
import com.safe_ride.rides_service.model.entity.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RideRepository extends JpaRepository<Ride, UUID> {

    long countByCreatedByUserIdAndStatus(UUID createdByUserId, RideStatus status);

    long countByDriverIdAndStatus(UUID driverId, RideStatus status);
}