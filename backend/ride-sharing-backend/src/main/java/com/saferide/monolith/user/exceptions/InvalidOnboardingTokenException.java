package com.saferide.monolith.user.exceptions;

/** The onboarding token backing a {@code /select-role} call is missing, malformed or expired. */
public class InvalidOnboardingTokenException extends RuntimeException {
    public InvalidOnboardingTokenException(String message) {
        super(message);
    }
}
