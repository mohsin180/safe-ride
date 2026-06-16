package com.saferide.notification_service.listener;

import com.saferide.notification_service.config.RabbitConfig;
import com.saferide.notification_service.event.RideNotificationEvent;
import com.saferide.notification_service.model.entity.Notification;
import com.saferide.notification_service.repo.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Consumes ride lifecycle events and turns each one into per-recipient
 * in-app notifications. Owns the notification copy (title/body) and the
 * client-facing {@code type} that maps to an icon.
 */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationRepository repository;

    public NotificationListener(NotificationRepository repository) {
        this.repository = repository;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onRideEvent(RideNotificationEvent event) {
        if (event == null || event.type() == null
                || event.recipients() == null || event.recipients().isEmpty()) {
            return;
        }

        String drop = event.drop() != null ? event.drop() : "your destination";
        String pickup = event.pickup() != null ? event.pickup() : "your pickup";
        Content content = renderByType(event, pickup, drop);
        List<Notification> batch = new ArrayList<>();
        for (UUID recipient : event.recipients()) {
            if (recipient == null) {
                continue;
            }
            Notification n = new Notification();
            n.setRecipientUserId(recipient);
            n.setType(content.notifType);
            n.setTitle(content.title);
            n.setBody(content.body);
            n.setRideId(event.rideId());
            // An actionable rate-prompt carries who to rate so the client can
            // open the rating sheet straight from the notification tap.
            if ("RATE_PROMPT".equals(event.type())) {
                n.setSubjectUserId(event.actorId());
                n.setSubjectName(event.actorName());
            } else if ("JOIN_REQUEST".equals(event.type())) {
                // The host's card needs the requester's identity, rating, route,
                // and the request id to accept/decline.
                n.setSubjectUserId(event.actorId());
                n.setSubjectName(event.actorName());
                n.setSubjectRating(event.rating());
                n.setRequestId(event.requestId());
                n.setPickup(event.pickup());
                n.setDrop(event.drop());
            }
            n.setRead(false);
            batch.add(n);
        }
        repository.saveAll(batch);
        log.info("Stored {} notification(s) for {}", batch.size(), event.type());
    }

    private Content renderByType(RideNotificationEvent e, String pickup, String drop) {
        return switch (e.type()) {
            case "RIDE_CREATED" -> new Content("SYSTEM", "Ride requested",
                    "Your ride to " + drop + " is posted. Hang tight for a driver.");
            case "RIDE_ACCEPTED" -> new Content("TRIP", "Ride accepted",
                    "A driver accepted your ride to " + drop + ".");
            case "RIDE_STARTED" -> new Content("TRIP", "Ride started",
                    "Your trip to " + drop + " is on the way.");
            case "RIDE_COMPLETED" -> new Content("TRIP", "Ride completed",
                    "Your trip to " + drop + " is complete.");
            case "RIDE_CANCELLED" -> new Content("TRIP", "Ride cancelled",
                    "The ride from " + pickup + " was cancelled.");
            case "DRIVER_CANCELLED" -> new Content("TRIP", "Driver unavailable",
                    "Your driver dropped the ride to " + drop
                            + " — we're finding you another driver.");
            case "RATING_RECEIVED" -> new Content("RATING", "New rating",
                    e.stars() != null
                            ? "You received a " + e.stars() + "-star rating."
                            : "You received a new rating.");
            case "RATE_PROMPT" -> new Content("RATING",
                    "Rate " + (e.actorName() != null ? e.actorName() : "your co-passenger"),
                    (e.actorName() != null ? e.actorName() : "A co-passenger")
                            + " left the ride at their stop. Tap to rate them.");
            case "JOIN_REQUEST" -> new Content("REQUEST",
                    (e.actorName() != null ? e.actorName() : "Someone") + " wants to join",
                    (e.actorName() != null ? e.actorName() : "A passenger")
                            + " asked to join your ride. Accept or decline below.");
            case "JOIN_ACCEPTED" -> new Content("TRIP", "Request accepted",
                    "You're in! Your ride to " + drop + " is confirmed.");
            case "JOIN_DECLINED" -> new Content("REQUEST", "Request declined",
                    "Your request to join the ride to " + drop + " was declined.");
            default -> new Content("SYSTEM", "Update",
                    "There's an update on your ride.");
        };
    }

    private record Content(String notifType, String title, String body) {
    }
}
