package com.safe_ride.rides_service.model.dtos;

public record RideStatsResponse(
        long trips,
        Double rating
) {
}