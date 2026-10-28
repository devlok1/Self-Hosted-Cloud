package com.homecloud.backend.controller;

import com.homecloud.backend.dto.LoginRequest;
import com.homecloud.backend.dto.LoginResponse;
import com.homecloud.backend.dto.RegisterRequest;
import com.homecloud.backend.dto.UserResponse;
import com.homecloud.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserResponse> registerJson(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.registerUser(request.getEmail(), request.getPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> registerForm(
            @RequestParam("email") String email,
            @RequestParam("password") String password) {
        UserResponse response = authService.registerUser(email, password);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LoginResponse> loginJson(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.loginUser(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> loginForm(
            @RequestParam("email") String email,
            @RequestParam("password") String password) {
        LoginResponse response = authService.loginUser(email, password);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        UserResponse response = authService.getCurrentUserProfile(authentication.getName());
        return ResponseEntity.ok(response);
    }
}