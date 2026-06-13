package com.safe_ride.rides_service.repo;

import com.safe_ride.rides_service.model.entity.RideParticipants;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RideParticipantsRepository extends JpaRepository<RideParticipants, UUID> {

    long countByRide_Id(UUID rideId);

    boolean existsByRide_IdAndUserId(UUID rideId, UUID userId);

    long deleteByRide_IdAndUserId(UUID rideId, UUID userId);

    /** Co-passenger userIds for a ride — used to address notifications. */
    @Query("SELECT p.userId FROM RideParticipants p WHERE p.ride.id = :rideId")
    List<UUID> findUserIdsByRideId(@Param("rideId") UUID rideId);
}
