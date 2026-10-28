package com.homecloud.backend.dto;

import com.homecloud.backend.entity.User;

public class UserResponse {

    private Long id;
    private String email;

    public UserResponse() {}

    public UserResponse(Long id, String email) {
        this.id = id;
        this.email = email;
    }

    public static UserResponse fromEntity(User user) {
        if (user == null) return null;
        return new UserResponse(user.getId(), user.getEmail());
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }
}
