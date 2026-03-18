package com.saferide.profile_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final GatewayAuthFilter gatewayAuth;

    public SecurityConfig(GatewayAuthFilter gatewayAuth) {
        this.gatewayAuth = gatewayAuth;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity security) {
        return security.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(gatewayAuth, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
