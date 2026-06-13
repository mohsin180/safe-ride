package com.saferide.messaging_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket setup. Clients connect at {@code /api/v1/messages/ws}
 * (the gateway proxies this path + injects X-User-Id on the upgrade),
 * subscribe to {@code /topic/chat.<rideId>}, and SEND to
 * {@code /app/chat/<rideId>}.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final UserIdHandshakeInterceptor handshakeInterceptor;
    private final UserIdHandshakeHandler handshakeHandler;
    private final SubscribeAuthInterceptor subscribeAuthInterceptor;

    public WebSocketConfig(UserIdHandshakeInterceptor handshakeInterceptor,
                           UserIdHandshakeHandler handshakeHandler,
                           SubscribeAuthInterceptor subscribeAuthInterceptor) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.handshakeHandler = handshakeHandler;
        this.subscribeAuthInterceptor = subscribeAuthInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/api/v1/messages/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(handshakeInterceptor)
                .setHandshakeHandler(handshakeHandler);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(subscribeAuthInterceptor);
    }
}
