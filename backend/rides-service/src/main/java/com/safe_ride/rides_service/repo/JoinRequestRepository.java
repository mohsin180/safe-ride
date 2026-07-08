package com.safe_ride.rides_service.repo;

import com.safe_ride.rides_service.model.entity.JoinRequest;
import com.safe_ride.rides_service.model.entity.JoinRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JoinRequestRepository extends JpaRepository<JoinRequest, UUID> {

    boolean existsByRideIdAndRequesterIdAndStatus(
            UUID rideId, UUID requesterId, JoinRequestStatus status);

    /** The requester's most recent request for a ride — carries their own
     *  route so ride details can show the viewer their personal pickup/drop
     *  (not the host's). Most-recent so a re-request after a decline wins. */
    Optional<JoinRequest> findFirstByRideIdAndRequesterIdOrderByCreatedAtDesc(
            UUID rideId, UUID requesterId);

    /** All requests for a ride in a given status — used to gather every
     *  accepted co-passenger's route so the map can draw the full multi-stop
     *  polyline (host pickup/drop + each rider's pickup/drop). */
    List<JoinRequest> findByRideIdAndStatus(UUID rideId, JoinRequestStatus status);
}
