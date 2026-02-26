package com.saferide.api_gateway.config;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

public class JwtUtils{

    public static String extractSub(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            return claims.getSubject(); // sub
        } catch (Exception e) {
            throw new RuntimeException("Invalid JWT token", e);
        }
    }
}