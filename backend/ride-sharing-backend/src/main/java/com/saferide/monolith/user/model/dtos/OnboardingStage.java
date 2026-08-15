package com.saferide.monolith.user.model.dtos;

/**
 * The step a signup is waiting on. The server decides this — the app just
 * renders the matching screen — so the two can never disagree about where a
 * half-finished user belongs.
 */
public enum OnboardingStage {
    /** Registered; waiting for the emailed link to be clicked. */
    EMAIL_VERIFICATION,
    /** Email proven; no role chosen yet. */
    ROLE,
    /** Role chosen; name / phone / CNIC still missing. */
    PROFILE,
    /** Driver only: profile filled, vehicle details still missing. */
    VEHICLE,
    /** Everything typed; identity verification is the last gate. */
    KYC,
    /** Verified and promoted — a real account now exists, with a real token. */
    COMPLETE
}
