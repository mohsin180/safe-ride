package com.saferide.monolith.rides.model.dtos;

public record RideStatsResponse(
        long trips,
        Double rating,
        long ratingCount
) {
}