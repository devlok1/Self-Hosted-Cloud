package com.homecloud.backend.controller;

import com.homecloud.backend.entity.Folder;
import com.homecloud.backend.entity.User;
import com.homecloud.backend.repository.UserRepository;
import com.homecloud.backend.service.FolderService;
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
    public ResponseEntity<?> createFolder(
            @RequestParam("name") String name,
            @RequestParam(value = "parentId", required = false) Long parentId,
            Authentication authentication) {
        try {
            User user = authenticatedUser(authentication);
            return ResponseEntity.ok(folderService.createFolder(name, parentId, user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", message(e)));
        }
    }

    @GetMapping
    public ResponseEntity<List<Folder>> listFolders(
            @RequestParam(value = "parentId", required = false) Long parentId,
            Authentication authentication) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(folderService.listFolders(parentId, user.getId()));
    }

    @PutMapping("/{folderId}")
    public ResponseEntity<?> renameFolder(
            @PathVariable Long folderId,
            @RequestParam("name") String name,
            Authentication authentication) {
        try {
            User user = authenticatedUser(authentication);
            return ResponseEntity.ok(folderService.renameFolder(folderId, name, user.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", message(e)));
        }
    }

    @DeleteMapping("/{folderId}")
    public ResponseEntity<?> deleteFolder(
            @PathVariable Long folderId,
            Authentication authentication) {
        try {
            User user = authenticatedUser(authentication);
            folderService.deleteFolder(folderId, user.getId());
            return ResponseEntity.ok(Map.of("message", "Folder deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", message(e)));
        }
    }

    private User authenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Authentication required");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    private String message(Exception e) {
        return e.getMessage() == null ? "Request failed" : e.getMessage();
    }
}
