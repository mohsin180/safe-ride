package com.safe_ride.rides_service.model.dtos;

/**
 * Outcome of a rider cancelling a ride: the fee recorded (0 when inside the
 * free window, before the driver arrived), its currency, and the rider's
 * running count of fee-bearing cancellations so the client can warn repeat
 * cancellers.
 */
public record CancellationResult(
        double fee,
        String currency,
        long strikeCount
) {
}
