package com.homecloud.backend.controller;

import com.homecloud.backend.dto.FolderDetailResponse;
import com.homecloud.backend.dto.FolderResponse;
import com.homecloud.backend.entity.Folder;
import com.homecloud.backend.entity.User;
import com.homecloud.backend.exception.UnauthorizedException;
import com.homecloud.backend.repository.UserRepository;
import com.homecloud.backend.service.FolderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderService folderService;
    private final UserRepository userRepository;

    public FolderController(FolderService folderService, UserRepository userRepository) {
        this.folderService = folderService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<FolderResponse> createFolder(
            @RequestParam("name") String name,
            @RequestParam(value = "parentId", required = false) Long parentId,
            Authentication authentication) {

        User user = authenticatedUser(authentication);
        Folder created = folderService.createFolder(name, parentId, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(FolderResponse.fromEntity(created));
    }

    @GetMapping
    public ResponseEntity<List<FolderResponse>> listFolders(
            @RequestParam(value = "parentId", required = false) Long parentId,
            Authentication authentication) {

        User user = authenticatedUser(authentication);
        List<Folder> folders = folderService.listFolders(parentId, user.getId());
        List<FolderResponse> response = folders.stream()
                .map(FolderResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{folderId}")
    public ResponseEntity<FolderDetailResponse> getFolderDetails(
            @PathVariable Long folderId,
            Authentication authentication) {

        User user = authenticatedUser(authentication);
        FolderDetailResponse response = folderService.getFolderDetails(folderId, user.getId());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{folderId}")
    public ResponseEntity<FolderResponse> renameFolder(
            @PathVariable Long folderId,
            @RequestParam("name") String name,
            Authentication authentication) {

        User user = authenticatedUser(authentication);
        Folder updated = folderService.renameFolder(folderId, name, user.getId());
        return ResponseEntity.ok(FolderResponse.fromEntity(updated));
    }

    @DeleteMapping("/{folderId}")
    public ResponseEntity<Map<String, String>> deleteFolder(
            @PathVariable Long folderId,
            Authentication authentication) {

        User user = authenticatedUser(authentication);
        folderService.deleteFolder(folderId, user.getId());
        return ResponseEntity.ok(Map.of("message", "Folder deleted successfully"));
    }

    private User authenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Authentication required");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
    }
}
