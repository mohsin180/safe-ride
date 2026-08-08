package com.saferide.monolith.user.model.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,
        @Size(min = 6, message = "Password must be at least 6 characters")
        @NotBlank(message = "Password is required")
        String password,
        /**
         * Gender decides which rides this account can ever see or join, so an
         * unchecked value used to mean a blank or bogus string reached the
         * database and the JWT. Constrained to the two {@code Gender} enum
         * names so the claim is always meaningful.
         */
        @NotNull(message = "Gender is required")
        @Pattern(regexp = "MALE|FEMALE", message = "Gender must be MALE or FEMALE")
        String gender
) {
}
