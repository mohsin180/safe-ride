package com.saferide.monolith.rides.model.entity;

public enum DriverOfferStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    /**
     * The driver was assigned, then dropped the ride. Distinct from DECLINED
     * (which is the host rejecting) and retired from ACCEPTED so the
     * duplicate-offer check can't be sidestepped by re-offering on a ride the
     * driver just abandoned.
     */
    WITHDRAWN
}
