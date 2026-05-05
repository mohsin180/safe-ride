package com.safe_ride.rides_service.config;

import java.util.UUID;

public record UserContext(
        UUID userId,
        String role,
        String gender
) {
}