package com.saferide.user_service.model.dtos;

import com.saferide.user_service.model.enums.Gender;
import com.saferide.user_service.model.enums.Role;

public record RegisterRequest(
        String username,
        String email,
        String password,
        Role role,
        Gender gender
) {
}
