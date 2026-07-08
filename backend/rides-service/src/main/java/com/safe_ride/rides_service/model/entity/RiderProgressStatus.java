package com.safe_ride.rides_service.model.entity;

public enum RiderProgressStatus {
    /** Not yet picked up. */
    WAITING,
    /** In the vehicle. */
    PICKED,
    /** Dropped at their stop — their fare is settled. */
    DROPPED
}
