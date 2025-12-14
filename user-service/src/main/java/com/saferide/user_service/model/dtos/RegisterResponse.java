package com.saferide.user_service.model.dtos;

import com.saferide.user_service.model.enums.Gender;
import com.saferide.user_service.model.enums.Role;

import java.util.UUID;

public record RegisterResponse(
        UUID keycloakId,
        String username,
        String email,
        Role role,
        Gender gender
) {
}
