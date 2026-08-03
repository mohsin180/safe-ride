package com.saferide.monolith.user.exceptions;

public record ErrorResponse(
        Integer status,
        String message
) {
}
