package com.saferide.monolith.user.model.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Corrects the gender picked at signup. Only MALE/FEMALE are accepted, for
 * the same reason as {@link RegisterRequest}: the value ends up in the JWT
 * and decides which rides the account can ever see.
 */
public record GenderChangeRequest(
        @NotNull(message = "Gender is required")
        @Pattern(regexp = "MALE|FEMALE", message = "Gender must be MALE or FEMALE")
        String gender
) {
}
