package com.safe_ride.user_service.model.dtos;

import lombok.Builder;

@Builder
public record LoginResponse(
        String token,
        String email,
        String role,
        String gender
) {
}
