package com.safe_ride.rides_service.model.entity;

public enum PaymentStatus {
    /** Fare is owed — the driver hasn't collected the cash yet. */
    PENDING,
    /** The driver has confirmed they collected this rider's cash fare. */
    COLLECTED
}
