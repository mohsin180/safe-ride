package com.safe_ride.rides_service.repo;

import com.safe_ride.rides_service.model.entity.Ride;
import com.safe_ride.rides_service.model.entity.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RideRepository extends JpaRepository<Ride, UUID> {

    long countByCreatedByUserIdAndStatus(UUID createdByUserId, RideStatus status);

    boolean existsByCreatedByUserIdAndStatusIn(UUID createdByUserId, Collection<RideStatus> statuses);

    long countByDriverIdAndStatus(UUID driverId, RideStatus status);

    @Query("SELECT r FROM Ride r " +
            "WHERE r.status IN (com.safe_ride.rides_service.model.entity.RideStatus.PENDING, " +
            "                   com.safe_ride.rides_service.model.entity.RideStatus.ACCEPTED) " +
            "AND r.createdByUserId <> :currentUserId " +
            "AND r.availableSeats > 0 " +
            "AND r.id NOT IN (SELECT p.ride.id FROM RideParticipants p WHERE p.userId = :currentUserId) " +
            "ORDER BY r.createdAt DESC")
    List<Ride> findAvailableRides(@Param("currentUserId") UUID currentUserId);

    @Query("SELECT r FROM Ride r " +
            "WHERE r.createdByUserId = :userId " +
            "AND r.status IN (com.safe_ride.rides_service.model.entity.RideStatus.PENDING, " +
            "                 com.safe_ride.rides_service.model.entity.RideStatus.ACCEPTED) " +
            "ORDER BY r.createdAt DESC")
    List<Ride> findMyActiveRides(@Param("userId") UUID userId);
}