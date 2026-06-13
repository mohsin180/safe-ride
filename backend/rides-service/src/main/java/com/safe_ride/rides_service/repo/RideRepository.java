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

    /**
     * Whether the user is committed to an active ride — as host OR as a
     * co-passenger. Enforces "one ride at a time" on create / join / find.
     */
    @Query("SELECT COUNT(r) > 0 FROM Ride r " +
            "WHERE r.status IN (com.safe_ride.rides_service.model.entity.RideStatus.PENDING, " +
            "                   com.safe_ride.rides_service.model.entity.RideStatus.ACCEPTED) " +
            "AND (r.createdByUserId = :userId " +
            "     OR r.id IN (SELECT p.ride.id FROM RideParticipants p WHERE p.userId = :userId))")
    boolean hasActiveRideOrJoined(@Param("userId") UUID userId);

    /**
     * The user's active ride(s) — whether they host it or joined it. Drives
     * the "Your Rides" tab so a co-passenger's confirmed ride shows there too.
     */
    @Query("SELECT r FROM Ride r " +
            "WHERE r.status IN (com.safe_ride.rides_service.model.entity.RideStatus.PENDING, " +
            "                   com.safe_ride.rides_service.model.entity.RideStatus.ACCEPTED) " +
            "AND (r.createdByUserId = :userId " +
            "     OR r.id IN (SELECT p.ride.id FROM RideParticipants p WHERE p.userId = :userId)) " +
            "ORDER BY r.createdAt DESC")
    List<Ride> findMyActiveOrJoinedRides(@Param("userId") UUID userId);

    @Query("SELECT r FROM Ride r " +
            "WHERE r.createdByUserId = :userId " +
            "AND r.status IN (com.safe_ride.rides_service.model.entity.RideStatus.COMPLETED, " +
            "                 com.safe_ride.rides_service.model.entity.RideStatus.CANCELLED) " +
            "ORDER BY r.createdAt DESC")
    List<Ride> findMyRideHistory(@Param("userId") UUID userId);

    /**
     * Fresh ride requests a driver can claim: still PENDING (no driver yet)
     * and with seats left. Drivers aren't participants, so unlike the
     * passenger feed there's no participant sub-query or creator filter.
     */
    @Query("SELECT r FROM Ride r " +
            "WHERE r.status = com.safe_ride.rides_service.model.entity.RideStatus.PENDING " +
            "AND r.availableSeats > 0 " +
            "ORDER BY r.createdAt DESC")
    List<Ride> findDriverFeed();

    /**
     * The ride(s) a driver is currently running — ACCEPTED (en route to
     * pickup) or STARTED (trip in progress). Drives the driver's active-trip
     * screen and the single-active-ride guard on accept.
     */
    @Query("SELECT r FROM Ride r " +
            "WHERE r.driverId = :driverId " +
            "AND r.status IN (com.safe_ride.rides_service.model.entity.RideStatus.ACCEPTED, " +
            "                 com.safe_ride.rides_service.model.entity.RideStatus.STARTED) " +
            "ORDER BY r.createdAt DESC")
    List<Ride> findDriverActiveRides(@Param("driverId") UUID driverId);

    /**
     * The passenger's in-progress trip — a ride they host OR joined that's
     * ACCEPTED (driver en route) or STARTED (in transit). Drives the
     * passenger active-trip screen + live tracking.
     */
    @Query("SELECT r FROM Ride r " +
            "WHERE r.status IN (com.safe_ride.rides_service.model.entity.RideStatus.ACCEPTED, " +
            "                   com.safe_ride.rides_service.model.entity.RideStatus.STARTED) " +
            "AND (r.createdByUserId = :userId " +
            "     OR r.id IN (SELECT p.ride.id FROM RideParticipants p WHERE p.userId = :userId)) " +
            "ORDER BY r.createdAt DESC")
    List<Ride> findMyActiveTrip(@Param("userId") UUID userId);

    /**
     * Every ride a user belongs to a chat for — as host, as the assigned
     * driver, or as a co-passenger — excluding cancelled rides. Drives the
     * messaging-service chat list.
     */
    @Query("SELECT DISTINCT r FROM Ride r " +
            "WHERE r.status <> com.safe_ride.rides_service.model.entity.RideStatus.CANCELLED " +
            "AND (r.createdByUserId = :userId " +
            "     OR r.driverId = :userId " +
            "     OR r.id IN (SELECT p.ride.id FROM RideParticipants p WHERE p.userId = :userId)) " +
            "ORDER BY r.createdAt DESC")
    List<Ride> findChatsForUser(@Param("userId") UUID userId);
}