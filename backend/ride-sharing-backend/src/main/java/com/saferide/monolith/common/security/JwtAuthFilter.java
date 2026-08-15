package com.saferide.monolith.common.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Replaces the old api-gateway's {@code JwtForwardingFilter} + each service's
 * {@code GatewayAuthFilter} with a single in-process filter:
 *
 * <ol>
 *   <li>Validates the {@code Authorization: Bearer} token (except on the
 *       public {@code /api/v1/auth/**} paths).</li>
 *   <li>Populates the {@link SecurityContextHolder} with a
 *       {@code ROLE_<role>} authority and a {@link UserContext} in the
 *       authentication details — exactly what the service code expects.</li>
 *   <li>Rewrites the request's {@code X-User-*} headers from the token
 *       claims, so controllers using {@code @RequestHeader("X-User-Id")} and
 *       the WebSocket handshake interceptor keep working unchanged. Any
 *       client-supplied {@code X-User-*} headers are stripped — they can no
 *       longer be spoofed (unlike the old direct-to-service ports).</li>
 * </ol>
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/"
    );

    /**
     * Endpoints that sit under a public prefix but act on the caller's own
     * record, so they still need the token parsed here. SecurityConfig marks
     * them {@code authenticated()}; without this exemption the prefix match
     * below would skip token parsing, leave the SecurityContext empty, and
     * every call would 401 no matter how valid the Bearer token was.
     * Keep in sync with the explicit matchers in SecurityConfig.
     */
    private static final Map<String, String> AUTHENTICATED_UNDER_PUBLIC_PREFIX = Map.of(
            "/api/v1/auth/gender", "PUT"
    );

    private static final List<String> IDENTITY_HEADERS = List.of(
            "X-User-Id", "X-User-Role", "X-User-Gender", "X-User-Email"
    );

    private final JwtUtils jwtUtils;

    public JwtAuthFilter(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isPublicPath(path, request.getMethod())
                || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            // Never trust client-sent identity headers, even on public paths.
            filterChain.doFilter(new IdentityHeaderRewriter(request, Map.of()), response);
            return;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        String token = authHeader.substring(7);
        if (!jwtUtils.validateToken(token)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        try {
            Claims claims = jwtUtils.extractAllClaims(token);
            String userId = claims.get("userId", String.class);
            String role = claims.get("role", String.class);
            String gender = claims.get("gender", String.class);
            String email = claims.get("email", String.class);

            if (userId == null || role == null || gender == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            authentication.setDetails(
                    new UserContext(UUID.fromString(userId), role, gender, email));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            Map<String, String> identity = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            identity.put("X-User-Id", userId);
            identity.put("X-User-Role", role);
            identity.put("X-User-Gender", gender);
            identity.put("X-User-Email", email != null ? email : "");

            filterChain.doFilter(new IdentityHeaderRewriter(request, identity), response);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    private boolean isPublicPath(String path, String method) {
        if (method.equalsIgnoreCase(AUTHENTICATED_UNDER_PUBLIC_PREFIX.get(path))) {
            return false;
        }
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * Hides any inbound {@code X-User-*} headers and exposes the trusted
     * values extracted from the JWT instead.
     */
    private static class IdentityHeaderRewriter extends HttpServletRequestWrapper {

        private final Map<String, String> identity;

        IdentityHeaderRewriter(HttpServletRequest request, Map<String, String> identity) {
            super(request);
            this.identity = identity;
        }

        private boolean isIdentityHeader(String name) {
            return IDENTITY_HEADERS.stream().anyMatch(h -> h.equalsIgnoreCase(name));
        }

        @Override
        public String getHeader(String name) {
            if (isIdentityHeader(name)) {
                return identity.get(name);
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (isIdentityHeader(name)) {
                String value = identity.get(name);
                return value == null
                        ? Collections.emptyEnumeration()
                        : Collections.enumeration(List.of(value));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
            Enumeration<String> original = super.getHeaderNames();
            while (original.hasMoreElements()) {
                String name = original.nextElement();
                if (!isIdentityHeader(name)) {
                    names.add(name);
                }
            }
            names.addAll(identity.keySet());
            return Collections.enumeration(names);
        }
    }
}
