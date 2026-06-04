package com.safe_ride.user_service.model.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequest(
        @Email(message = "Invalid email format")
        @NotBlank(message = "Email is required")
        String email
) {
}
