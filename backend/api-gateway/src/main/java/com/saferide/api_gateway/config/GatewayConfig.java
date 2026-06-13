package com.saferide.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    // Downstream service URIs — overridable via environment variables.
    @Value("${USER_SERVICE_URL:http://localhost:8001}")
    private String userServiceUrl;

    @Value("${PROFILE_SERVICE_URL:http://localhost:8002}")
    private String profileServiceUrl;

    @Value("${RIDES_SERVICE_URL:http://localhost:8003}")
    private String ridesServiceUrl;

    @Value("${NOTIFICATION_SERVICE_URL:http://localhost:8004}")
    private String notificationServiceUrl;

    @Value("${MESSAGING_SERVICE_URL:http://localhost:8005}")
    private String messagingServiceUrl;

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("user-services", r -> r
                        .path("/api/v1/auth/**")
                        .uri(userServiceUrl)
                )
                .route("profile-service", r -> r
                        .path("/api/v1/profile/**")
                        .uri(profileServiceUrl)
                )
                .route("rides-service", r -> r
                        .path("/api/v1/rides/**")
                        .uri(ridesServiceUrl)
                )
                .route("notification-service", r -> r
                        .path("/api/v1/notifications/**")
                        .uri(notificationServiceUrl)
                )
                .route("messaging-service", r -> r
                        .path("/api/v1/messages/**")
                        .uri(messagingServiceUrl)
                )
                .build();
    }
}

