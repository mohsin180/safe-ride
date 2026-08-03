package com.saferide.monolith.rides.repo;

import com.saferide.monolith.rides.model.entity.Gender;
import com.saferide.monolith.rides.model.entity.Ride;
import com.saferide.monolith.rides.model.entity.RideStatus;
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

    /**
     * A passenger's completed-trip count: COMPLETED rides they took part in,
     * whether they HOSTED it OR JOINED it as a co-passenger. DISTINCT so a
     * ride is never double-counted. Drives the "Trips" stat for passengers.
     */
    @Query("SELECT COUNT(DISTINCT r.id) FROM Ride r " +
            "WHERE r.status = com.saferide.monolith.rides.model.entity.RideStatus.COMPLETED " +
            "AND (r.createdByUserId = :userId " +
            "     OR r.id IN (SELECT p.ride.id FROM RideParticipants p WHERE p.userId = :userId))")
    long countCompletedTripsForUser(@Param("userId") UUID userId);

    @Query("SELECT r FROM Ride r " +
            "WHERE r.status = com.saferide.monolith.rides.model.entity.RideStatus.PENDING " +
            "AND r.createdByUserId <> :currentUserId " +
            "AND r.gender = :gender " +
            "AND r.availableSeats > 0 " +
            "AND r.id NOT IN (SELECT p.ride.id FROM RideParticipants p WHERE p.userId = :currentUserId) " +
            "ORDER BY r.createdAt DESC")
    List<Ride> findAvailableRides(@Param("currentUserId") UUID currentUserId,
                                  @Param("gender") Gender gender);

    /**
     * Same as {@link #findAvailableRides} but spatially bounded: only rides
     * whose pickup is within {@code radiusMeters} of the rider, ordered
     * nearest-first. Backed by the PostGIS GiST index on {@code pickup_geog}
     * — {@code ST_DWithin} for the radius, the {@code <->} KNN operator for the
     * order. Native because these are PostGIS operators JPQL can't express;
     * gender is bound as its String name (the column stores the enum name).
     */
    @Query(value = "SELECT r.* FROM ride r "
            + "WHERE r.status = 'PENDING' "
            + "AND r.created_by_user_id <> :uid "
            + "AND r.gender = :gender "
            + "AND r.available_seats > 0 "
            + "AND r.id NOT IN (SELECT p.ride_id FROM ride_participants p WHERE p.user_id = :uid) "
            + "AND ST_DWithin(r.pickup_geog, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters) "
            + "ORDER BY r.pickup_geog <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography",
            nativeQuery = true)
    List<Ride> findAvailableRidesNearby(@Param("uid") UUID uid,
                                        @Param("gender") String gender,
                                        @Param("lat") double lat,
                                        @Param("lng") double lng,
                                        @Param("radiusMeters") double radiusMeters);

    @Query("SELECT r FROM Ride r " +
            "WHERE r.createdByUserId = :userId " +
            "AND r.status IN (com.saferide.monolith.rides.model.entity.RideStatus.PENDING, " +
            "                 com.saferide.monolith.rides.model.entity.RideStatus.ACCEPTED) " +
            "ORDER BY r.createdAt DESC")
    List<Ride> findMyActiveRides(@Param("userId") UUID userId);

    /**
     * Whether the user is committed to an active ride — as host OR as a
     * co-passenger. Enforces "one ride at a time" on create / join / find.
     */
    @Query("SELECT COUNT(r) > 0 FROM Ride r " +
            "WHERE r.status IN (com.saferide.monolith.rides.model.entity.RideStatus.PENDING, " +
            "                   com.saferide.monolith.rides.model.entity.RideStatus.ACCEPTED) " +
            "AND (r.createdByUserId = :userId " +
            "     OR r.id IN (SELECT p.ride.id FROM RideParticipants p WHERE p.userId = :userId))")
    boolean hasActiveRideOrJoined(@Param("userId") UUID userId);

    /**
     * The user's active ride(s) — whether they host it or joined it. Drives
     * the "Your Rides" tab so a co-passenger's confirmed ride shows there too.
     */
    @Query("SELECT r FROM Ride r " +
            "WHERE r.status IN (com.saferide.monolith.rides.model.entity.RideStatus.PENDING, " +
            "                   com.saferide.monolith.rides.model.entity.RideStatus.ACCEPTED) " +
            "AND (r.createdByUserId = :userId " +
            "     OR r.id IN (SELECT p.ride.id FROM RideParticipants p WHERE p.userId = :userId)) " +
            "ORDER BY r.createdAt DESC")
    List<Ride> findMyActiveOrJoinedRides(@Param("userId") UUID userId);

    /**
     * The user's finished rides — COMPLETED or CANCELLED — whether they
     * HOSTED the ride OR JOINED it as a co-passenger, so a co-passenger's
     * past rides show in their history too (mirrors the join clause in
     * {@link #findMyActiveOrJoinedRides}). Newest-first.
     */
    @Query("SELECT r FROM Ride r " +
            "WHERE r.status IN (com.saferide.monolith.rides.model.entity.RideStatus.COMPLETED, " +
            "                   com.saferide.monolith.rides.model.entity.RideStatus.CANCELLED) " +
            "AND (r.createdByUserId = :userId " +
            "     OR r.id IN (SELECT p.ride.id FROM RideParticipants p WHERE p.userId = :userId)) " +
            "ORDER BY r.createdAt DESC")
    List<Ride> findMyRideHistory(@Param("userId") UUID userId);

    /**
     * The driver's finished rides — COMPLETED or CANCELLED — that they were
     * assigned to. Drives the driver ride-history screen, newest-first.
     */
    @Query("SELECT r FROM Ride r " +
            "WHERE r.driverId = :driverId " +
            "AND r.status IN (com.saferide.monolith.rides.model.entity.RideStatus.COMPLETED, " +
            "                 com.saferide.monolith.rides.model.entity.RideStatus.CANCELLED) " +
            "ORDER BY r.createdAt DESC")
    List<Ride> findDriverRideHistory(@Param("driverId") UUID driverId);

    /**
     * The driver's COMPLETED rides — drives the earnings totals (lifetime +
     * today). Newest-first, though order doesn't affect the aggregates.
     */
    @Query("SELECT r FROM Ride r " +
            "WHERE r.driverId = :driverId " +
            "AND r.status = com.saferide.monolith.rides.model.entity.RideStatus.COMPLETED " +
            "ORDER BY r.createdAt DESC")
    List<Ride> findDriverCompletedRides(@Param("driverId") UUID driverId);

    /**
     * Fresh ride requests a driver can claim: still PENDING (no driver yet)
     * and explicitly published to drivers by the host. Seat availability is
     * NOT a condition — a passenger group that has filled all its seats still
     * needs a driver. Drivers aren't participants, so unlike the passenger
     * feed there's no participant sub-query or creator filter.
     */
    @Query("SELECT r FROM Ride r " +
            "WHERE r.status = com.saferide.monolith.rides.model.entity.RideStatus.PENDING " +
            "AND r.publishedToDrivers = true " +
            "ORDER BY r.createdAt DESC")
    List<Ride> findDriverFeed();

    /**
     * Same as {@link #findDriverFeed} but spatially bounded to PENDING rides
     * within {@code radiusMeters} of the driver, ordered nearest-first via the
     * PostGIS GiST index on {@code pickup_geog}. No gender filter — drivers
     * aren't gender-restricted. Native for the PostGIS operators.
     */
    @Query(value = "SELECT r.* FROM ride r "
            + "WHERE r.status = 'PENDING' "
            + "AND r.published_to_drivers = true "
            + "AND ST_DWithin(r.pickup_geog, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters) "
            + "ORDER BY r.pickup_geog <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography",
            nativeQuery = true)
    List<Ride> findDriverFeedNearby(@Param("lat") double lat,
                                    @Param("lng") double lng,
                                    @Param("radiusMeters") double radiusMeters);

    /**
     * The ride(s) a driver is currently running — ACCEPTED (en route to
     * pickup) or STARTED (trip in progress). Drives the driver's active-trip
     * screen and the single-active-ride guard on accept.
     */
    @Query("SELECT r FROM Ride r " +
            "WHERE r.driverId = :driverId " +
            "AND r.status IN (com.saferide.monolith.rides.model.entity.RideStatus.ACCEPTED, " +
            "                 com.saferide.monolith.rides.model.entity.RideStatus.ARRIVED, " +
            "                 com.saferide.monolith.rides.model.entity.RideStatus.STARTED) " +
            "ORDER BY r.createdAt DESC")
    List<Ride> findDriverActiveRides(@Param("driverId") UUID driverId);

    /**
     * The passenger's in-progress trip — a ride they host OR joined that's
     * ACCEPTED (driver en route) or STARTED (in transit). Drives the
     * passenger active-trip screen + live tracking.
     */
    @Query("SELECT r FROM Ride r " +
            "WHERE r.status IN (com.saferide.monolith.rides.model.entity.RideStatus.ACCEPTED, " +
            "                   com.saferide.monolith.rides.model.entity.RideStatus.ARRIVED, " +
            "                   com.saferide.monolith.rides.model.entity.RideStatus.STARTED) " +
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
            "WHERE r.status <> com.saferide.monolith.rides.model.entity.RideStatus.CANCELLED " +
            "AND (r.createdByUserId = :userId " +
            "     OR r.driverId = :userId " +
            "     OR r.id IN (SELECT p.ride.id FROM RideParticipants p WHERE p.userId = :userId)) " +
            "ORDER BY r.createdAt DESC")
    List<Ride> findChatsForUser(@Param("userId") UUID userId);
}