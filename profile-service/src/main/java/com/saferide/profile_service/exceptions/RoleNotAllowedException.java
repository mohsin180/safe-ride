package com.saferide.profile_service.exceptions;

public class RoleNotAllowedException extends RuntimeException {
    public RoleNotAllowedException(String message) {
        super(message);
    }
}
