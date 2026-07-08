package com.saferide.messaging_service.controller;

import com.saferide.messaging_service.client.RidesClient;
import com.saferide.messaging_service.model.dtos.LocationUpdate;
import com.saferide.messaging_service.model.entity.RideDriverLocation;
import com.saferide.messaging_service.repo.RideDriverLocationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST fallback for live tracking: the driver's last-known position for a
 * ride. The live map streams positions over STOMP, but a rider who opens the
 * app late has no history on the topic — this lets them fetch the car's last
 * spot once, up front, so the marker appears immediately. Nested under the
 * {@code /api/v1/messages/**} gateway route. Membership-guarded, same rule as
 * the track topic.
 */
@RestController
@RequestMapping("/api/v1/messages/track")
public class TrackingRestController {

    private final RideDriverLocationRepository driverLocationRepository;
    private final RidesClient ridesClient;

    public TrackingRestController(RideDriverLocationRepository driverLocationRepository,
                                  RidesClient ridesClient) {
        this.driverLocationRepository = driverLocationRepository;
        this.ridesClient = ridesClient;
    }

    /** The driver's last-known position for this ride, or 204 if none yet. */
    @GetMapping("/{rideId}/last")
    public ResponseEntity<LocationUpdate> lastDriverLocation(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable("rideId") UUID rideId) {
        // Only members of the ride may read its driver location.
        if (!ridesClient.isMember(rideId, userId)) {
            return ResponseEntity.status(403).build();
        }
        return driverLocationRepository.findById(rideId)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private LocationUpdate toDto(RideDriverLocation loc) {
        return new LocationUpdate(
                loc.getDriverId(), "DRIVER", loc.getLat(), loc.getLng(),
                loc.getBearing(), loc.getUpdatedAt());
    }
}
