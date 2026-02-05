package com.saferide.api_gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("user-service", r -> r
                        .path("/api/v1/user/**")
                        .uri("http://localhost:8001")
                )
                .route("profile-service", r -> r
                        .path("/api/v1/profile/**")
                        .uri("http://localhost:8002")
                )
                .build();
    }
}

