package com.saferide.monolith.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.expiration}")
    private long expiration;

    private static final String ONBOARDING_PURPOSE = "ONBOARDING";
    /** Matches the verification-email window, so a link opened next day still works. */
    private static final long ONBOARDING_EXPIRATION_MS = 24 * 60 * 60 * 1000L;

    public String generateToken(UUID userId, String role, String gender, String email) {
        return Jwts.builder()
                .subject(userId.toString())
                .claims(Map.of(
                        "userId", userId,
                        "role", role,
                        "gender", gender,
                        "email", email
                )).issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * A short-lived token that proves nothing except "you own this account and
     * you're still onboarding". Handed out at registration and by a login that
     * can't issue a real token yet because no role is chosen. It carries no
     * role, so {@code JwtAuthFilter} rejects it for ordinary API calls — its
     * only use is authorising {@code /select-role}, which used to trust a raw
     * user id in the URL and would let anyone set a stranger's role.
     */
    public String generateOnboardingToken(UUID userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .claims(Map.of(
                        "userId", userId,
                        "purpose", ONBOARDING_PURPOSE
                )).issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ONBOARDING_EXPIRATION_MS))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * The user id inside a valid onboarding token.
     *
     * @throws JwtException if the token is malformed, expired, signed with the
     *                      wrong key, or is a normal access token rather than
     *                      an onboarding one
     */
    public UUID parseOnboardingToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!ONBOARDING_PURPOSE.equals(claims.get("purpose", String.class))) {
            throw new JwtException("Not an onboarding token");
        }
        return UUID.fromString(claims.getSubject());
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }


}
