package com.saferide.user_service.exceptions;

import org.springframework.http.HttpStatusCode;

public record LoginErrorResponse(
        HttpStatusCode statusCode,
        String message
) {
}
