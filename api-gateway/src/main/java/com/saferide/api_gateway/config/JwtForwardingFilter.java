package com.saferide.api_gateway.config;


import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtForwardingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .cast(JwtAuthenticationToken.class)
                .flatMap(jwtAuth -> {
                    String userId = jwtAuth.getToken().getClaimAsString("sub");
                    String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
                    String role = jwtAuth.getToken().getClaimAsString("role");
                    ServerWebExchange mutated = exchange.mutate()
                            .request(r -> r
                                    .header("X-User-Id", userId)
                                    .header(HttpHeaders.AUTHORIZATION, authorization)
                                    .header("X-User-Role", role)
                            )
                            .build();

                    return chain.filter(mutated);
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
