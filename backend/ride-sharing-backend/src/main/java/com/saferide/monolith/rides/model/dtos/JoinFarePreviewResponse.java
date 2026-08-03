package com.saferide.monolith.rides.model.dtos;

/**
 * What a prospective co-passenger would pay if they joined a ride with their
 * own route + seats: their weighted share of the simulated new trip, plus the
 * new trip metrics. Haversine-simulated (no routing-API call) — accurate
 * enough to decide on.
 */
public record JoinFarePreviewResponse(
        double yourShare,
        double hostShare,
        double gross,
        double tripKm,
        int tripMin,
        String currency
) {
}
