package com.saferide.profile_service.models.dtos;

import jakarta.validation.constraints.NotBlank;

public record PassengerProfileRequest(
        @NotBlank(message = "FullName is required")
        String fullName,
        @NotBlank(message = "CNIC is required")
        String cnic,
        @NotBlank(message = "Role is required")
        Role role
) {
}
