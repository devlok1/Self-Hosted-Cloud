package com.homecloud.backend.service;

import com.homecloud.backend.dto.LoginResponse;
import com.homecloud.backend.dto.UserResponse;
import com.homecloud.backend.entity.User;
import com.homecloud.backend.exception.BadRequestException;
import com.homecloud.backend.exception.ConflictException;
import com.homecloud.backend.exception.UnauthorizedException;
import com.homecloud.backend.repository.UserRepository;
import com.homecloud.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(userRepository, jwtService, passwordEncoder);
    }

    @Test
    @DisplayName("Register user with valid email and password succeeds")
    void registerUser_success() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            return new User(u.getEmail(), u.getPassword());
        });

        UserResponse response = authService.registerUser("Alice@Example.COM", "securePassword123");

        assertNotNull(response);
        assertEquals("alice@example.com", response.getEmail());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Registering existing email throws ConflictException")
    void registerUser_duplicateEmail_throwsConflict() {
        User existing = new User("bob@example.com", "hash");
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class, () ->
                authService.registerUser("bob@example.com", "password123"));
    }

    @Test
    @DisplayName("Registering with invalid email throws BadRequestException")
    void registerUser_invalidEmail_throwsBadRequest() {
        assertThrows(BadRequestException.class, () ->
                authService.registerUser("invalid-email", "password123"));
    }

    @Test
    @DisplayName("Registering with short password (<6 chars) throws BadRequestException")
    void registerUser_shortPassword_throwsBadRequest() {
        assertThrows(BadRequestException.class, () ->
                authService.registerUser("valid@example.com", "12345"));
    }

    @Test
    @DisplayName("Login with valid credentials succeeds and returns JWT token")
    void loginUser_success() {
        String hashedPassword = passwordEncoder.encode("secretPassword123");
        User user = new User("charlie@example.com", hashedPassword);
        when(userRepository.findByEmail("charlie@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(), eq("charlie@example.com"))).thenReturn("mock.jwt.token");

        LoginResponse response = authService.loginUser("Charlie@Example.com", "secretPassword123");

        assertNotNull(response);
        assertEquals("Login successful", response.getMessage());
        assertEquals("charlie@example.com", response.getEmail());
        assertEquals("mock.jwt.token", response.getToken());
    }

    @Test
    @DisplayName("Login with incorrect password throws UnauthorizedException")
    void loginUser_wrongPassword_throwsUnauthorized() {
        String hashedPassword = passwordEncoder.encode("correctPassword");
        User user = new User("dave@example.com", hashedPassword);
        when(userRepository.findByEmail("dave@example.com")).thenReturn(Optional.of(user));

        assertThrows(UnauthorizedException.class, () ->
                authService.loginUser("dave@example.com", "wrongPassword"));
    }

    @Test
    @DisplayName("Login with non-existent email throws UnauthorizedException")
    void loginUser_unknownEmail_throwsUnauthorized() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () ->
                authService.loginUser("unknown@example.com", "anyPassword"));
    }
}
