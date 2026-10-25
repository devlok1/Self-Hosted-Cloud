package com.homecloud.backend.service;

import com.homecloud.backend.dto.LoginResponse;
import com.homecloud.backend.entity.User;
import com.homecloud.backend.repository.UserRepository;
import com.homecloud.backend.security.JwtService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public AuthService(
            UserRepository userRepository,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public User registerUser(String email, String password) {

        Optional<User> existingUser =
                userRepository.findByEmail(email);

        if (existingUser.isPresent()) {
            throw new RuntimeException("Email already registered");
        }

        String hashedPassword =
                passwordEncoder.encode(password);

        User user =
                new User(email, hashedPassword);

        return userRepository.save(user);
    }

    public LoginResponse loginUser(
            String email,
            String password) {

        Optional<User> userOptional =
                userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            throw new RuntimeException("Invalid email or password");
        }

        User user = userOptional.get();

        if (!passwordEncoder.matches(
                password,
                user.getPassword())) {

            throw new RuntimeException("Invalid email or password");
        }

        String token =
                jwtService.generateToken(
                        user.getId(),
                        user.getEmail());

        return new LoginResponse(
                "Login successful",
                user.getId(),
                user.getEmail(),
                token
        );
    }
}