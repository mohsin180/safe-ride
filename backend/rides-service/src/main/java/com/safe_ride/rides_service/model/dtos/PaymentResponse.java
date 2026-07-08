package com.safe_ride.rides_service.model.dtos;

/**
 * One rider's cash fare on a ride, for the driver's collect-cash sheet and the
 * rider's own paid/unpaid indicator.
 */
public record PaymentResponse(
        String userId,
        String name,
        double amount,
        String currency,
        String method,
        String status,   // "PENDING" | "COLLECTED"
        boolean isHost
) {
}
