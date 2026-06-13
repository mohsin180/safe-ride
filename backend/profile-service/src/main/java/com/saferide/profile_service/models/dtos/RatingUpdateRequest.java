package com.saferide.profile_service.models.dtos;

/**
 * Internal payload from rides-service to cache a user's freshly-recomputed
 * average rating onto their profile.
 */
public record RatingUpdateRequest(
        Double rating
) {
}
