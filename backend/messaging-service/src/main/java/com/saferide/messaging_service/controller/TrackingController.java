package com.saferide.messaging_service.controller;

import com.saferide.messaging_service.client.RidesClient;
import com.saferide.messaging_service.model.dtos.LocationUpdate;
import com.saferide.messaging_service.model.entity.RideDriverLocation;
import com.saferide.messaging_service.repo.RideDriverLocationRepository;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

/**
 * Live location relay. A ride member SENDs its GPS to
 * {@code /app/track/<rideId>}; the server stamps the authoritative sender
 * id, then broadcasts to {@code /topic/track.<rideId>} so every other
 * member's map can move that marker. Positions are ephemeral — nothing is
 * persisted (the broker just fans them out in real time).
 */
@Controller
public class TrackingController {

    /** Topic a ride's members subscribe to for live positions. */
    public static final String TOPIC_PREFIX = "/topic/track.";

    private final RidesClient ridesClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final RideDriverLocationRepository driverLocationRepository;

    public TrackingController(RidesClient ridesClient,
                             SimpMessagingTemplate messagingTemplate,
                             RideDriverLocationRepository driverLocationRepository) {
        this.ridesClient = ridesClient;
        this.messagingTemplate = messagingTemplate;
        this.driverLocationRepository = driverLocationRepository;
    }

    @MessageMapping("/track/{rideId}")
    public void track(@DestinationVariable UUID rideId,
                      LocationUpdate payload,
                      Principal principal) {
        if (principal == null || payload == null) {
            return;
        }
        UUID userId = UUID.fromString(principal.getName());
        // Only members of the ride may publish to its track topic.
        if (!ridesClient.isMember(rideId, userId)) {
            return;
        }
        Instant now = Instant.now();
        LocationUpdate out = new LocationUpdate(
                userId, payload.role(), payload.lat(), payload.lng(),
                payload.bearing(), now);
        messagingTemplate.convertAndSend(TOPIC_PREFIX + rideId, out);

        // Persist the driver's last-known spot so a rider who opens the app
        // late (or missed the live frames) can be shown the car immediately.
        // Best-effort — a storage hiccup must never break the live relay.
        if (payload.role() != null && "DRIVER".equalsIgnoreCase(payload.role())) {
            try {
                driverLocationRepository.save(new RideDriverLocation(
                        rideId, userId, payload.lat(), payload.lng(),
                        payload.bearing(), now));
            } catch (Exception ignored) {
                // swallow — the broadcast already went out
            }
        }
    }
}
