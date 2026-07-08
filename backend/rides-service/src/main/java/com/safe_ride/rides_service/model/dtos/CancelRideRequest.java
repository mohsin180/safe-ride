package com.safe_ride.rides_service.model.dtos;

/** Optional body for cancelling a ride — carries the rider's reason, if given. */
public record CancelRideRequest(
        String reason
) {
}
