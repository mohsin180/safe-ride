package com.saferide.monolith.rides.model.entity;

public enum RiderProgressStatus {
    /** Not yet picked up. */
    WAITING,
    /** In the vehicle. */
    PICKED,
    /** Dropped at their stop — their fare is settled. */
    DROPPED
}
