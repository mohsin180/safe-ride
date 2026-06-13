package com.saferide.api_gateway.config;


import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtForwardingFilter implements GlobalFilter, Ordered {
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/"
    );
    private final JwtUtils jwtUtils;

    public JwtForwardingFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Service-to-service endpoints (e.g. /api/v1/profile/internal/**) must
        // only be reachable by other services calling each other directly —
        // never from the public side through the gateway. Reject with 404 so
        // the path's existence isn't revealed to clients.
        if (isInternalPath(path)) {
            return onNotFound(exchange);
        }

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return onUnauthorized(exchange);
        }
        String token = authHeader.substring(7);
        if (!jwtUtils.validateToken(token)) {
            return onUnauthorized(exchange);
        }
        try {
            Claims claims = jwtUtils.extractAllClaims(token);
            String userId = claims.get("userId", String.class);
            String role = claims.get("role", String.class);
            String gender = claims.get("gender", String.class);
            String email = claims.get("email", String.class);

            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Role", role)
                    .header("X-User-Gender", gender)
                    .header("X-User-Email", email != null ? email : "")
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            return onUnauthorized(exchange);
        }

    }

    @Override
    public int getOrder() {
        return -1;
    }

    public boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    /** Matches any service's internal-only path segment, e.g.
     *  {@code /api/v1/profile/internal/...}. */
    public boolean isInternalPath(String path) {
        return path.contains("/internal/") || path.endsWith("/internal");
    }

    private Mono<Void> onUnauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }

    private Mono<Void> onNotFound(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.NOT_FOUND);
        return response.setComplete();
    }
}
