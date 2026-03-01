package com.safe_ride.user_service.model.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String gender,
        String role,
        boolean enabled,
        LocalDateTime createdAt
) {
}
