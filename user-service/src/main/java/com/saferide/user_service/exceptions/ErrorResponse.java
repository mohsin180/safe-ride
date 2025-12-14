package com.saferide.user_service.exceptions;

public record ErrorResponse(
        Integer status,
        String message
) {
}
