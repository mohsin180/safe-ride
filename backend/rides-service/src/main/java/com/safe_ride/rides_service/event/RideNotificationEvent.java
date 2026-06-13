package com.safe_ride.rides_service.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A ride lifecycle event published to notification-service. Carries the
 * resolved recipient list (computed here, where host/driver/participants
 * are known) plus enough context for the consumer to render copy. Sent as
 * JSON — notification-service has a matching-field copy of this type.
 */
public record RideNotificationEvent(
        String type,
        UUID rideId,
        List<UUID> recipients,
        String pickup,
        String drop,
        Integer stars,
        // The person the event is about — used by RATE_PROMPT so recipients
        // know which co-passenger to rate. Null for events with no subject.
        UUID actorId,
        String actorName,
        // JOIN_REQUEST extras: the request to act on + the requester's rating.
        // pickup/drop above carry the requester's own route for these.
        UUID requestId,
        Double rating,
        Instant occurredAt
) {
    public static final String RIDE_CREATED = "RIDE_CREATED";
    public static final String RIDE_ACCEPTED = "RIDE_ACCEPTED";
    public static final String RIDE_STARTED = "RIDE_STARTED";
    public static final String RIDE_COMPLETED = "RIDE_COMPLETED";
    public static final String RIDE_CANCELLED = "RIDE_CANCELLED";
    /** The assigned driver dropped the ride; it's re-opened for another driver. */
    public static final String DRIVER_CANCELLED = "DRIVER_CANCELLED";
    public static final String RIDE_JOINED = "RIDE_JOINED";
    public static final String RIDE_LEFT = "RIDE_LEFT";
    public static final String RATING_RECEIVED = "RATING_RECEIVED";
    /** A co-passenger left at their stop — remaining riders are prompted to rate them. */
    public static final String RATE_PROMPT = "RATE_PROMPT";
    /** A co-passenger asked to join — the host accepts or declines. */
    public static final String JOIN_REQUEST = "JOIN_REQUEST";
    /** The host accepted a join request. */
    public static final String JOIN_ACCEPTED = "JOIN_ACCEPTED";
    /** The host declined a join request. */
    public static final String JOIN_DECLINED = "JOIN_DECLINED";
}
