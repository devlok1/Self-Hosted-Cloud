package com.homecloud.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationTime;

    public JwtService(
            @Value("${homecloud.jwt.secret:change_me_to_a_secure_jwt_secret_with_at_least_32_characters}") String secret,
            @Value("${homecloud.jwt.expiration-ms:3600000}") long expirationTime) {

        if (secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters long");
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

    public String extractEmail(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    public boolean isValid(String token) {
        try {
            extractEmail(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
