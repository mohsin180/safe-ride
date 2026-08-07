package com.saferide.monolith.kyc.model;

import java.time.LocalDateTime;

/**
 * What the app sees of the caller's KYC state.
 *
 * <p>{@code sessionToken} and {@code verificationUrl} are both only present
 * right after a session is created: the Flutter app feeds the token to
 * Didit's native SDK, and falls back to opening the URL in a browser if the
 * SDK can't run. Both are null on status polls.
 *
 * @param status          one of {@link KycStatus}, as a string
 * @param sessionToken    token for Didit's native mobile SDK
 * @param verificationUrl Didit's hosted flow URL (browser fallback)
 * @param verifiedAt      when the user was approved, null otherwise
 * @param rejectionReason why verification was refused — set when the scanned
 *                        CNIC contradicts the account (gender, CNIC number,
 *                        age, expiry) so the app can say what to fix
 */
public record KycStatusResponse(String status, String sessionToken, String verificationUrl,
                                LocalDateTime verifiedAt, String rejectionReason) {

    /** A plain status with no in-flight session attached. */
    public static KycStatusResponse of(KycStatus status, LocalDateTime verifiedAt) {
        return new KycStatusResponse(status.name(), null, null, verifiedAt, null);
    }
}
