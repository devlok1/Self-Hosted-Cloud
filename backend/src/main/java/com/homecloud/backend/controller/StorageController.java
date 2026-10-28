package com.homecloud.backend.controller;

import com.homecloud.backend.dto.StorageStatsResponse;
import com.homecloud.backend.entity.User;
import com.homecloud.backend.exception.UnauthorizedException;
import com.homecloud.backend.repository.UserRepository;
import com.homecloud.backend.service.FileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/storage")
public class StorageController {

    private final FileService fileService;
    private final UserRepository userRepository;

    public StorageController(FileService fileService, UserRepository userRepository) {
        this.fileService = fileService;
        this.userRepository = userRepository;
    }

    @GetMapping("/stats")
    public ResponseEntity<StorageStatsResponse> getStorageStats(Authentication authentication) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(fileService.getStorageStats(user.getId()));
    }

    private User authenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Authentication required");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
    }
}
