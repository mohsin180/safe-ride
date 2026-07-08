package com.safe_ride.rides_service.repo;

import com.safe_ride.rides_service.model.entity.RideCancellation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Rider cancellation history — used for strike counts and fee settlement. */
public interface RideCancellationRepository
        extends JpaRepository<RideCancellation, UUID> {

    /** How many fee-bearing (late) cancellations this rider has racked up. */
    long countByUserIdAndFeeGreaterThan(UUID userId, double fee);
}
