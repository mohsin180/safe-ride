package com.saferide.user_service.model.dtos;

import com.saferide.user_service.model.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank(message = "Username is required")
        String username,
        @NotBlank(message = "Email is required")
        @Email(message = "Type an email syntax")
        String email,
        @NotBlank(message = "password is required")
        String password,
        @NotBlank(message = "phoneNo is required")
        String phoneNo,
        @NotBlank(message = "Gender is required")
        Gender gender
) {
}
