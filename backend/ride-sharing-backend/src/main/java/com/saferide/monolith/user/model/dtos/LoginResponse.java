package com.saferide.monolith.user.model.dtos;

import lombok.Builder;

import java.util.UUID;

/**
 * Login result. A verified user with a role gets {@code token} and nothing
 * else; a user who still has to pick a role gets no token but does get
 * {@code roleRequired} plus the {@code userId}/{@code onboardingToken} needed
 * to finish. Returning that instead of an error is what lets someone whose app
 * was killed mid-signup recover — the id is otherwise only ever handed out in
 * the register response, which can't be replayed.
 */
@Builder
public record LoginResponse(
        String token,
        UUID userId,
        boolean roleRequired,
        String onboardingToken
) {
}
