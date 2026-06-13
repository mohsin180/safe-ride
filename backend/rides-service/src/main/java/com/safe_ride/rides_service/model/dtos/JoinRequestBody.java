package com.safe_ride.rides_service.model.dtos;

/**
 * Body for requesting to join a ride. Carries the requester's OWN route
 * (from their search) so the host can see where they want to go before
 * accepting. All fields optional — omitted values fall back to the ride's
 * own pickup/destination.
 */
public record JoinRequestBody(
        String pickup,
        Double pickupLat,
        Double pickupLng,
        String drop,
        Double dropLat,
        Double dropLng
) {
}
