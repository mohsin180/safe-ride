package com.saferide.user_service.model.dtos;

public record LoginRequest(
        String username,
        String password
) {
}
