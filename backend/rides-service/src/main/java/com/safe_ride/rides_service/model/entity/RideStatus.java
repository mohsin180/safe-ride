package com.safe_ride.rides_service.model.entity;

public enum RideStatus {
    PENDING,
    ACCEPTED,
    // The assigned driver has reached the pickup point and is waiting for the
    // rider(s) to board. Sits between ACCEPTED (en route) and STARTED (moving).
    ARRIVED,
    STARTED,
    COMPLETED,
    CANCELLED
}