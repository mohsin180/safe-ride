package com.saferide.monolith.user.model.dtos;

import lombok.Builder;

@Builder
public record LoginResponse(
        String token
) {
}
