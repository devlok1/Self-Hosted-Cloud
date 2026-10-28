package com.homecloud.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String TEST_SECRET = "abcdefghijklmnopqrstuvwxyz1234567890!@#$%^&*()";
    private static final long EXPIRATION_MS = 3600000; // 1 hour

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, EXPIRATION_MS);
    }

    @Test
    @DisplayName("Short JWT secret throws IllegalArgumentException")
    void constructor_rejectsShortSecret() {
        assertThrows(IllegalArgumentException.class, () ->
                new JwtService("too_short_secret", EXPIRATION_MS));
    }

    @Test
    @DisplayName("Generate token and extract email and userId")
    void generateToken_extractsClaimsSuccessfully() {
        Long userId = 42L;
        String email = "testuser@example.com";

        String token = jwtService.generateToken(userId, email);
        assertNotNull(token);
        assertFalse(token.isBlank());

        assertEquals(email, jwtService.extractEmail(token));
        assertEquals(userId, jwtService.extractUserId(token));
        assertTrue(jwtService.isValid(token));
    }

    @Test
    @DisplayName("Tampered token fails validation")
    void isValid_returnsFalseForTamperedToken() {
        String token = jwtService.generateToken(1L, "user@example.com");
        String tamperedToken = token.substring(0, token.length() - 5) + "abcde";

        assertFalse(jwtService.isValid(tamperedToken));
    }

    @Test
    @DisplayName("Expired token is marked invalid")
    void isValid_returnsFalseForExpiredToken() {
        JwtService shortLivedService = new JwtService(TEST_SECRET, -1000);
        String expiredToken = shortLivedService.generateToken(1L, "expired@example.com");

        assertFalse(shortLivedService.isValid(expiredToken));
    }
}
