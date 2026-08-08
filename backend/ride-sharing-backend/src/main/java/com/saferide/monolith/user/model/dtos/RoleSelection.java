package com.saferide.monolith.user.model.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RoleSelection(
        /** Unvalidated this reached {@code Role.valueOf}, so a null or typo
         *  surfaced as a 500 instead of a readable 400. */
        @NotNull(message = "Role is required")
        @Pattern(regexp = "DRIVER|PASSENGER", message = "Role must be DRIVER or PASSENGER")
        String role
) {
}
