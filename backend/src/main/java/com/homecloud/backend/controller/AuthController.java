package com.homecloud.backend.controller;

import com.homecloud.backend.dto.LoginResponse;
import com.homecloud.backend.entity.User;
import com.homecloud.backend.service.AuthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public User register(
            @RequestParam String email,
            @RequestParam String password) {

        return authService.registerUser(email, password);
    }
    @PostMapping("/login")
    public LoginResponse login(
            @RequestParam String email,
            @RequestParam String password) {

        return authService.loginUser(email, password);
    }
}