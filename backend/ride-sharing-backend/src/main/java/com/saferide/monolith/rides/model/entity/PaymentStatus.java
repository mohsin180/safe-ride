package com.saferide.monolith.rides.model.entity;

public enum PaymentStatus {
    /** Fare is owed — the driver hasn't collected the cash yet. */
    PENDING,
    /** The driver has confirmed they collected this rider's cash fare. */
    COLLECTED,
    /**
     * Nothing is owed: the rider never boarded. Kept as a row rather than
     * deleted so the trip's ledger still shows who was expected, without
     * counting toward the driver's earnings.
     */
    CANCELLED
}
