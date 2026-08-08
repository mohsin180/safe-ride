package com.saferide.monolith.rides.model.entity;

/** Lifecycle of a co-passenger's request to join a ride. */
public enum JoinRequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    /**
     * Was accepted, then the rider left the ride. Retired rather than deleted
     * so the history survives — and so a later re-join doesn't collide with
     * it: every consumer keys accepted requests by requester and keeps the
     * first, which meant an abandoned route kept overriding the new one.
     */
    LEFT
}
