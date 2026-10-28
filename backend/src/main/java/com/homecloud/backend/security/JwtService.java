package com.homecloud.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String DEFAULT_INSECURE_SECRET = "change_me_to_a_secure_jwt_secret_with_at_least_32_characters";

    private final SecretKey secretKey;
    private final long expirationTime;

    public JwtService(
            @Value("${homecloud.jwt.secret:change_me_to_a_secure_jwt_secret_with_at_least_32_characters}") String secret,
            @Value("${homecloud.jwt.expiration-ms:3600000}") long expirationTime) {

        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalArgumentException("JWT secret must be configured and at least 32 characters long");
        }

        if (DEFAULT_INSECURE_SECRET.equals(secret.trim())) {
            log.warn("SECURITY WARNING: Using the default placeholder JWT secret! Configure 'JWT_SECRET' in .env for production use.");
        }

        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationTime = expirationTime;
    }

    public String generateToken(Long userId, String email) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        Object userIdClaim = extractAllClaims(token).get("userId");
        if (userIdClaim instanceof Number) {
            return ((Number) userIdClaim).longValue();
        }
        return null;
    }

    public boolean isValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getExpiration() != null && claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
