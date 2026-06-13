package com.safe_ride.rides_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

/**
 * Talks to profile-service directly (not through the gateway) to resolve
 * passenger userIds into display names + ratings. The URL defaults to the
 * local profile-service port and can be overridden with the
 * {@code profile.service.url} property.
 */
@FeignClient(name = "profile-service", url = "${profile.service.url:http://localhost:8002}")
public interface ProfileClient {

    @PostMapping("/api/v1/profile/internal/passengers/by-ids")
    List<PassengerSummary> getPassengerSummaries(@RequestBody List<UUID> userIds);

    @PostMapping("/api/v1/profile/internal/passengers/{userId}/rating")
    void updatePassengerRating(@PathVariable("userId") UUID userId,
                               @RequestBody RatingUpdateRequest request);
}
