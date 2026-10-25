package com.homecloud.backend.dto;

public class LoginResponse {

    private String message;
    private Long userId;
    private String email;
    private String token;

    public LoginResponse(
            String message,
            Long userId,
            String email,
            String token) {

        this.message = message;
        this.userId = userId;
        this.email = email;
        this.token = token;
    }

    public String getMessage() {
        return message;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getToken() {
        return token;
    }
}