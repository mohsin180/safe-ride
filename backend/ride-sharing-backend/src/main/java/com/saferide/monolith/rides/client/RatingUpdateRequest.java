package com.saferide.monolith.rides.client;

/**
 * Sent to profile-service to cache a user's freshly-recomputed average
 * rating onto their profile. rides-service owns the individual ratings;
 * profile-service just stores the aggregate for fast summary reads.
 */
public record RatingUpdateRequest(
        Double rating
) {
}
