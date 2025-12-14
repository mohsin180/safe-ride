package com.saferide.user_service.model.dtos;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Integer expiresIn
) {
}
