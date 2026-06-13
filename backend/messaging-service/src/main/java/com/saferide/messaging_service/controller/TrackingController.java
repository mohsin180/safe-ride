package com.saferide.messaging_service.controller;

import com.saferide.messaging_service.client.RidesClient;
import com.saferide.messaging_service.model.dtos.LocationUpdate;
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

    public TrackingController(RidesClient ridesClient,
                             SimpMessagingTemplate messagingTemplate) {
        this.ridesClient = ridesClient;
        this.messagingTemplate = messagingTemplate;
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
        LocationUpdate out = new LocationUpdate(
                userId, payload.role(), payload.lat(), payload.lng(),
                payload.bearing(), Instant.now());
        messagingTemplate.convertAndSend(TOPIC_PREFIX + rideId, out);
    }
}
