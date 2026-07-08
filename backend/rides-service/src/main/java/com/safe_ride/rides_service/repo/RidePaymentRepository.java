package com.safe_ride.rides_service.repo;

import com.safe_ride.rides_service.model.entity.RidePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Cash fare ledger — one row per rider per ride. */
public interface RidePaymentRepository extends JpaRepository<RidePayment, UUID> {

    List<RidePayment> findByRideId(UUID rideId);

    Optional<RidePayment> findByRideIdAndUserId(UUID rideId, UUID userId);

    /** This user's payments across the given rides — for the history "paid" flag. */
    List<RidePayment> findByUserIdAndRideIdIn(UUID userId, List<UUID> rideIds);

    boolean existsByRideId(UUID rideId);
}
