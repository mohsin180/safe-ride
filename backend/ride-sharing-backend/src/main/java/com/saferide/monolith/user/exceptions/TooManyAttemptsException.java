package com.saferide.monolith.user.exceptions;

/** An auth endpoint's per-key attempt budget is spent. Maps to HTTP 429. */
public class TooManyAttemptsException extends RuntimeException {
    public TooManyAttemptsException(String message) {
        super(message);
    }
}
