package com.saferide.monolith.common.security;

import java.util.UUID;

/** The authenticated caller, extracted from the JWT by {@link JwtAuthFilter}. */
public record UserContext(
        UUID userId,
        String role,
        String gender,
        String email
) {
}
