package com.saferide.monolith.kyc.exceptions;

/**
 * Thrown when an action needs a verified identity and the caller's KYC has
 * not been approved. Mapped to 403 by {@code RidesExceptionHandler}.
 */
public class KycRequiredException extends RuntimeException {
    public KycRequiredException(String message) {
        super(message);
    }
}
