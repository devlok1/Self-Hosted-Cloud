package com.homecloud.backend.service;

import com.homecloud.backend.dto.LoginResponse;
import com.homecloud.backend.dto.UserResponse;
import com.homecloud.backend.entity.User;
import com.homecloud.backend.exception.BadRequestException;
import com.homecloud.backend.exception.ConflictException;
import com.homecloud.backend.exception.UnauthorizedException;
import com.homecloud.backend.repository.UserRepository;
import com.homecloud.backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse registerUser(String rawEmail, String rawPassword) {
        String email = normalizeEmail(rawEmail);
        validateRegistrationInput(email, rawPassword);

        if (userRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("Email already registered");
        }

        String hashedPassword = passwordEncoder.encode(rawPassword.trim());
        User user = new User(email, hashedPassword);
        User savedUser = userRepository.save(user);

        return UserResponse.fromEntity(savedUser);
    }

    public LoginResponse loginUser(String rawEmail, String rawPassword) {
        String email = normalizeEmail(rawEmail);

        if (rawPassword == null || rawPassword.isBlank()) {
            throw new UnauthorizedException("Invalid email or password");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(rawPassword.trim(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail());

        return new LoginResponse(
                "Login successful",
                user.getId(),
                user.getEmail(),
                token
        );
    }

    public UserResponse getCurrentUserProfile(String email) {
        String normalized = normalizeEmail(email);
        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        return UserResponse.fromEntity(user);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new BadRequestException("Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validateRegistrationInput(String email, String password) {
        if (email.isBlank() || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BadRequestException("Invalid email format");
        }
        if (password == null || password.trim().length() < 6) {
            throw new BadRequestException("Password must be at least 6 characters long");
        }
        if (password.length() > 128) {
            throw new BadRequestException("Password cannot exceed 128 characters");
        }
    }
}