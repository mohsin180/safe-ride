package com.saferide.messaging_service.config;

import com.saferide.messaging_service.client.RidesClient;
import com.saferide.messaging_service.controller.TrackingController;
import com.saferide.messaging_service.service.ChatService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;

/**
 * Rejects a STOMP SUBSCRIBE to {@code /topic/chat.<rideId>} unless the
 * connected user is a member of that ride — so the broker never relays a
 * ride's messages to someone outside it. Depends on {@link RidesClient}
 * (not {@link ChatService}) to avoid a bean cycle with the broker config.
 */
@Component
public class SubscribeAuthInterceptor implements ChannelInterceptor {

    private final RidesClient ridesClient;

    public SubscribeAuthInterceptor(RidesClient ridesClient) {
        this.ridesClient = ridesClient;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (!StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }
        String destination = accessor.getDestination();
        // Guard both the chat and the live-track topics — same membership rule.
        String prefix = null;
        if (destination != null && destination.startsWith(ChatService.TOPIC_PREFIX)) {
            prefix = ChatService.TOPIC_PREFIX;
        } else if (destination != null && destination.startsWith(TrackingController.TOPIC_PREFIX)) {
            prefix = TrackingController.TOPIC_PREFIX;
        }
        if (prefix == null) {
            return message; // not a member-guarded topic
        }
        Principal user = accessor.getUser();
        if (user == null) {
            throw new MessagingException("Unauthenticated subscription");
        }
        try {
            UUID rideId = UUID.fromString(destination.substring(prefix.length()));
            UUID userId = UUID.fromString(user.getName());
            if (!ridesClient.isMember(rideId, userId)) {
                throw new MessagingException("Not a member of this ride");
            }
        } catch (IllegalArgumentException e) {
            throw new MessagingException("Invalid subscription");
        }
        return message;
    }
}
