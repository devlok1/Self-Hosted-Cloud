package com.homecloud.backend.controller;

import com.homecloud.backend.entity.File;
import com.homecloud.backend.entity.User;
import com.homecloud.backend.repository.UserRepository;
import com.homecloud.backend.service.FileService;
import com.homecloud.backend.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;

    public FileController(FileService fileService,
                          FileStorageService fileStorageService,
                          UserRepository userRepository) {
        this.fileService = fileService;
        this.fileStorageService = fileStorageService;
        this.userRepository = userRepository;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) Long folderId,
            Authentication authentication) {
        try {
            User user = authenticatedUser(authentication);
            File savedFile = fileService.uploadFile(file, user, folderId);
            return ResponseEntity.ok(savedFile);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", message(e)));
        }
    }

    @GetMapping
    public ResponseEntity<List<File>> listFiles(
            @RequestParam(value = "folderId", required = false) Long folderId,
            Authentication authentication) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(fileService.getUserFiles(user.getId(), folderId));
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long fileId,
            Authentication authentication) throws MalformedURLException {

        User user = authenticatedUser(authentication);
        File file = fileService.getUserFile(fileId, user.getId());
        Path path = fileStorageService.getFilePath(file.getStoredFilename());

        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new RuntimeException("Physical file not found");
        }

        Resource resource = new UrlResource(path.toUri());
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.getFilename())
                                .build().toString())
                .contentLength(file.getSize())
                .body(resource);
    }

    @PutMapping("/{fileId}")
    public ResponseEntity<?> renameFile(
            @PathVariable Long fileId,
            @RequestParam("filename") String filename,
            Authentication authentication) {
        try {
            User user = authenticatedUser(authentication);
            return ResponseEntity.ok(fileService.renameFile(fileId, user.getId(), filename));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", message(e)));
        }
    }

    @PutMapping("/{fileId}/move")
    public ResponseEntity<?> moveFile(
            @PathVariable Long fileId,
            @RequestParam(value = "folderId", required = false) Long folderId,
            Authentication authentication) {
        try {
            User user = authenticatedUser(authentication);
            return ResponseEntity.ok(fileService.moveFile(fileId, user.getId(), folderId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", message(e)));
        }
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<?> deleteFile(
            @PathVariable Long fileId,
            Authentication authentication) {
        try {
            User user = authenticatedUser(authentication);
            fileService.deleteFile(fileId, user.getId());
            return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
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
