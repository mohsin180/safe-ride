package com.saferide.user_service.model.dtos;

import java.time.LocalDateTime;

public record ProfileResponse(
        String fullName,
        String profilePicture,
        Double rating,
        LocalDateTime createdAt
) {
}
