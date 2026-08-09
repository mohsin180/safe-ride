package com.saferide.monolith.user.exceptions;

/** Gender can no longer be corrected because the account passed KYC. */
public class GenderLockedException extends RuntimeException {
    public GenderLockedException(String message) {
        super(message);
    }
}
