package com.saferide.monolith.rides.model.dtos;

/** Optional body for cancelling a ride — carries the rider's reason, if given. */
public record CancelRideRequest(
        String reason
) {
}
