package com.saferide.profile_service.exceptions;

public record ErrorResponse(
        Integer status,
        String message
) {
}
