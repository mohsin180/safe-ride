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
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    public TrackingRestController(RideDriverLocationRepository driverLocationRepository,
                                  RidesClient ridesClient,
                                  org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate) {
        this.driverLocationRepository = driverLocationRepository;
        this.ridesClient = ridesClient;
        this.messagingTemplate = messagingTemplate;
    }

    /** Body for the REST position push. */
    public record LocationPush(String role, double lat, double lng, Double bearing) {
    }

    /**
     * REST fallback for PUBLISHING a position — used by the driver app when
     * the STOMP WebSocket can't connect (e.g. a proxy that blocks upgrades),
     * so riders still get the car's location. Persists the driver's last-known
     * spot and rebroadcasts on the live topic for any socket-connected member.
     */
    @PostMapping("/{rideId}")
    public ResponseEntity<Void> publish(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable("rideId") UUID rideId,
            @RequestBody LocationPush body) {
        if (body == null || !ridesClient.isMember(rideId, userId)) {
            return ResponseEntity.status(403).build();
        }
        java.time.Instant now = java.time.Instant.now();
        LocationUpdate out = new LocationUpdate(
                userId, body.role(), body.lat(), body.lng(), body.bearing(), now);
        try {
            messagingTemplate.convertAndSend(
                    TrackingController.TOPIC_PREFIX + rideId, out);
        } catch (Exception ignored) {
            // broadcast is best-effort; persistence below is what matters
        }
        if (body.role() != null && "DRIVER".equalsIgnoreCase(body.role())) {
            try {
                driverLocationRepository.save(new RideDriverLocation(
                        rideId, userId, body.lat(), body.lng(), body.bearing(), now));
            } catch (Exception ignored) {
            }
        }
        return ResponseEntity.noContent().build();
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
