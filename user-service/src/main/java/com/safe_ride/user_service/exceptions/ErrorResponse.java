package com.safe_ride.user_service.exceptions;

public record ErrorResponse(
        Integer status,
        String message
) {
}
