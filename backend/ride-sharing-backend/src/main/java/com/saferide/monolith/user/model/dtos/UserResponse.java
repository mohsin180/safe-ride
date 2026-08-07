package com.saferide.monolith.user.model.dtos;

import java.util.UUID;

/**
 * Registration result. The {@code onboardingToken} authorises the follow-up
 * {@code /select-role} call; the app stores it so onboarding survives the app
 * being killed while the user is off verifying their email.
 */
public record UserResponse(
        UUID id,
        String email,
        String onboardingToken
) {
}
