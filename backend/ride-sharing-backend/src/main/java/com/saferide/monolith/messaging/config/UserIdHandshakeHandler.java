package com.saferide.monolith.messaging.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Turns the {@code userId} captured at handshake into the STOMP session
 * {@link Principal}, so {@code @MessageMapping} handlers and the subscribe
 * interceptor know who is connected.
 */
@Component
public class UserIdHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        Object userId = attributes.get("userId");
        if (userId == null) {
            return null;
        }
        final String name = userId.toString();
        return () -> name;
    }
}
