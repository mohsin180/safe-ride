package com.saferide.user_service.model.dtos;

import com.saferide.user_service.model.enums.Gender;

public record RegisterResponse(
        String username,
        String email,
        Gender gender,
        String keycloakId
) {
}
