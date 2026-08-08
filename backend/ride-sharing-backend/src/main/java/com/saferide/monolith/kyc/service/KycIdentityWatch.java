package com.saferide.monolith.kyc.service;

import com.saferide.monolith.kyc.model.KycStatus;
import com.saferide.monolith.kyc.model.KycVerifiable;

import java.util.Objects;

/**
 * Keeps an APPROVED badge tied to the identity it was granted for.
 *
 * <p>Profile edits go through a mapper that overwrites name and CNIC but left
 * the KYC columns untouched, so a user could verify honestly with their own
 * card, then rename the profile and swap the CNIC and keep displaying
 * "verified" — with someone else's identity shown to hosts and drivers, and
 * {@code KycGuard} still waving them through.
 */
public final class KycIdentityWatch {

    private KycIdentityWatch() {
    }

    /**
     * Clears the verification when the identity fields no longer match what
     * was verified. Call AFTER the incoming values have been applied, passing
     * the values captured before.
     *
     * @return true when the badge was revoked, so callers can log or notify
     */
    public static boolean resetIfIdentityChanged(KycVerifiable profile,
                                                 String previousCnic,
                                                 String previousFullName) {
        if (matches(previousCnic, profile.getCnic())
                && matches(previousFullName, profile.getFullName())) {
            return false;
        }
        profile.setKycStatus(KycStatus.NOT_STARTED);
        profile.setKycVerifiedAt(null);
        profile.setKycSessionId(null);
        profile.setKycRejectionReason(
                "Your name or CNIC changed, so your identity needs verifying again.");
        return true;
    }

    /** Case- and spacing-insensitive: "  ali  khan" is not a new identity. */
    private static boolean matches(String before, String after) {
        return Objects.equals(normalise(before), normalise(after));
    }

    private static String normalise(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", " ").toUpperCase();
    }
}
