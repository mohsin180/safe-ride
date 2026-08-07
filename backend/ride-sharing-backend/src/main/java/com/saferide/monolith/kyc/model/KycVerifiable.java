package com.saferide.monolith.kyc.model;

import java.time.LocalDateTime;

/**
 * The KYC columns shared by {@code DriverProfile} and
 * {@code PassengerProfile}. Both are Lombok {@code @Data} entities, so the
 * generated accessors satisfy this interface for free — it exists so
 * {@code KycService} can run one code path for either role.
 */
public interface KycVerifiable {

    KycStatus getKycStatus();

    void setKycStatus(KycStatus status);

    /** Didit's session id for the in-flight verification, null when none. */
    String getKycSessionId();

    void setKycSessionId(String sessionId);

    LocalDateTime getKycVerifiedAt();

    void setKycVerifiedAt(LocalDateTime verifiedAt);

    /** Why the last verification was rejected, null when it wasn't. */
    String getKycRejectionReason();

    void setKycRejectionReason(String reason);

    /**
     * The CNIC the user typed during onboarding, cross-checked against the
     * one read off the scanned card.
     */
    String getCnic();

    /** The name the user typed during onboarding. */
    String getFullName();
}
