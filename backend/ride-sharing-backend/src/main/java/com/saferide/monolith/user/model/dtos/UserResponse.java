package com.saferide.monolith.user.model.dtos;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email
) {
}
