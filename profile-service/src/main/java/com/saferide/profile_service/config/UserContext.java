package com.saferide.profile_service.config;

import java.util.UUID;

public record UserContext(
        UUID userId,
        String role,
        String gender
) {
}
