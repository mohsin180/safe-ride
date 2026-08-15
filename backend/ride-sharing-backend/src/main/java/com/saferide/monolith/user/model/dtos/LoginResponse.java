package com.saferide.monolith.user.model.dtos;

import lombok.Builder;

import java.util.UUID;

/**
 * Login result, in one of exactly two shapes.
 *
 * <p>A finished account gets {@code token} and {@code onboardingStage =
 * COMPLETE}. A signup still in flight gets no token at all — there is no
 * account to hold a session — but does get the {@code onboardingStage} it
 * stopped at plus the {@code onboardingToken} to carry on with. Returning that
 * instead of an error is what lets someone who reinstalled the app resume
 * signup with nothing but their password.
 *
 * <p>The client routes on {@code onboardingStage} alone. It replaced a
 * {@code roleRequired} flag that could only describe one of the four places a
 * signup can stall, which is why login used to send half-finished users to the
 * home screen.
 */
@Builder
public record LoginResponse(
        String token,
        UUID userId,
        OnboardingStage onboardingStage,
        String onboardingToken
) {
}
