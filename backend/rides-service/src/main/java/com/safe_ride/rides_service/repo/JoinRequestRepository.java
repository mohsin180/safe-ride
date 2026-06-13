package com.safe_ride.rides_service.repo;

import com.safe_ride.rides_service.model.entity.JoinRequest;
import com.safe_ride.rides_service.model.entity.JoinRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JoinRequestRepository extends JpaRepository<JoinRequest, UUID> {

    boolean existsByRideIdAndRequesterIdAndStatus(
            UUID rideId, UUID requesterId, JoinRequestStatus status);
}
