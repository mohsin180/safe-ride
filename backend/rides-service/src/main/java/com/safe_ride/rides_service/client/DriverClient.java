package com.safe_ride.rides_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

/**
 * Talks to profile-service directly (not through the gateway) to resolve
 * driver userIds into a name + vehicle summary for completed-ride history.
 * Uses a distinct Feign {@code name} from {@link ProfileClient} (which
 * already owns "profile-service") to avoid a duplicate-client bean clash,
 * while pointing at the same {@code profile.service.url}.
 */
@FeignClient(name = "profile-driver", url = "${profile.service.url:http://localhost:8002}")
public interface DriverClient {

    @PostMapping("/api/v1/profile/internal/drivers/by-ids")
    List<DriverSummary> getDriverSummaries(@RequestBody List<UUID> userIds);

    @PostMapping("/api/v1/profile/internal/drivers/{userId}/rating")
    void updateDriverRating(@PathVariable("userId") UUID userId,
                            @RequestBody RatingUpdateRequest request);
}
