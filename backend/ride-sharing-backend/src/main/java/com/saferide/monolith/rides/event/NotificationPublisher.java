package com.saferide.monolith.rides.event;

import com.saferide.monolith.rides.model.entity.Ride;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Publishes ride lifecycle events as in-process Spring application events
 * (formerly RabbitMQ). The notification module consumes them asynchronously
 * via {@code @EventListener}. Best-effort by design: a publish failure is
 * swallowed and logged so notifications can never break the ride flow.
 */
@Component
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    private final ApplicationEventPublisher eventPublisher;

    public NotificationPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /** Publish an event for the given ride. {@code stars} is null except for ratings. */
    public void publish(String type, Ride ride, List<UUID> recipients, Integer stars) {
        publish(type, ride, recipients, stars, null, null);
    }

    public void publish(String type, Ride ride, List<UUID> recipients) {
        publish(type, ride, recipients, null, null, null);
    }

    /**
     * Full publish carrying a subject ({@code actorId}/{@code actorName}) —
     * used by RATE_PROMPT so recipients know which co-passenger to rate.
     */
    public void publish(String type, Ride ride, List<UUID> recipients, Integer stars,
                        UUID actorId, String actorName) {
        List<UUID> cleaned = recipients == null ? List.of()
                : recipients.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (cleaned.isEmpty()) {
            return;
        }
        String pickup = ride.getPickup() != null ? ride.getPickup().getAddress() : null;
        String drop = ride.getDestination() != null ? ride.getDestination().getAddress() : null;
        RideNotificationEvent event = new RideNotificationEvent(
                type, ride.getId(), cleaned, pickup, drop, stars, actorId, actorName,
                null, null, Instant.now());
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("Failed to publish {} for ride {}: {}", type, ride.getId(), e.getMessage());
        }
    }

    /**
     * A co-passenger's join request to the host. Carries the requester's
     * own route + rating + the request id so the host's notification can
     * render the card and its accept/decline actions.
     */
    public void publishJoinRequest(UUID rideId, List<UUID> recipients,
                                   UUID requesterId, String requesterName, Double requesterRating,
                                   UUID requestId, String pickup, String drop) {
        List<UUID> cleaned = recipients == null ? List.of()
                : recipients.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (cleaned.isEmpty()) {
            return;
        }
        RideNotificationEvent event = new RideNotificationEvent(
                RideNotificationEvent.JOIN_REQUEST, rideId, cleaned, pickup, drop, null,
                requesterId, requesterName, requestId, requesterRating, Instant.now());
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("Failed to publish JOIN_REQUEST for ride {}: {}", rideId, e.getMessage());
        }
    }

    /**
     * A driver's offer to drive, sent to the host. Carries the driver's
     * identity + rating + the offer id (reusing the requestId/rating fields)
     * so the host's notification can render the accept/decline card. pickup/
     * drop are the ride's own route, for context.
     */
    public void publishDriverOffer(UUID rideId, List<UUID> recipients,
                                   UUID driverId, String driverName, Double driverRating,
                                   UUID offerId, String pickup, String drop) {
        List<UUID> cleaned = recipients == null ? List.of()
                : recipients.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (cleaned.isEmpty()) {
            return;
        }
        RideNotificationEvent event = new RideNotificationEvent(
                RideNotificationEvent.DRIVER_OFFER, rideId, cleaned, pickup, drop, null,
                driverId, driverName, offerId, driverRating, Instant.now());
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("Failed to publish DRIVER_OFFER for ride {}: {}", rideId, e.getMessage());
        }
    }
}
