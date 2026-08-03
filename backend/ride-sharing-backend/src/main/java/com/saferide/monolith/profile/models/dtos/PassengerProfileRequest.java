package com.saferide.monolith.profile.models.dtos;

import jakarta.validation.constraints.NotBlank;

public record PassengerProfileRequest(
        @NotBlank(message = "FullName is required")
        String fullName,
        @NotBlank(message = "phone no is required")
        String phoneNo,
        @NotBlank(message = "CNIC is required")
        String cnic
) {
}
