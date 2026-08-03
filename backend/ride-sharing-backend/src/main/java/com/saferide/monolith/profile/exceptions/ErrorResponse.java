package com.saferide.monolith.profile.exceptions;

public record ErrorResponse(
        Integer status,
        String message
) {
}
