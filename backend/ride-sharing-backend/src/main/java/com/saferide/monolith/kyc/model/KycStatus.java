package com.saferide.monolith.kyc.model;

/**
 * Driver identity-verification state, persisted on
 * {@code DriverProfile.kycStatus}. Didit's session statuses (case-sensitive
 * strings like "Approved", "In Review") are collapsed into this simpler
 * app-facing state machine by {@code KycService}.
 */
public enum KycStatus {
    /** No verification session yet, or the last one expired/was abandoned. */
    NOT_STARTED,
    /** A Didit session exists and the driver is (or should be) completing it. */
    IN_PROGRESS,
    /** Didit flagged the session for manual review. */
    IN_REVIEW,
    /** Identity verified — CNIC parsed, liveness + face match passed. */
    APPROVED,
    /** Didit declined the verification; the driver may retry. */
    DECLINED
}
