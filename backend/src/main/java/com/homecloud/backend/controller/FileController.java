package com.homecloud.backend.controller;

import com.homecloud.backend.dto.FileResponse;
import com.homecloud.backend.dto.StorageStatsResponse;
import com.homecloud.backend.entity.File;
import com.homecloud.backend.entity.User;
import com.homecloud.backend.exception.ResourceNotFoundException;
import com.homecloud.backend.exception.UnauthorizedException;
import com.homecloud.backend.repository.UserRepository;
import com.homecloud.backend.service.FileService;
import com.homecloud.backend.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
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
    public ResponseEntity<FileResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) Long folderId,
            Authentication authentication) {

        User user = authenticatedUser(authentication);
        File savedFile = fileService.uploadFile(file, user, folderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(FileResponse.fromEntity(savedFile));
    }

    @GetMapping
    public ResponseEntity<List<FileResponse>> listFiles(
            @RequestParam(value = "folderId", required = false) Long folderId,
            Authentication authentication) {

        User user = authenticatedUser(authentication);
        List<File> files = fileService.getUserFiles(user.getId(), folderId);
        List<FileResponse> response = files.stream()
                .map(FileResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<List<FileResponse>> searchFiles(
            @RequestParam("q") String query,
            Authentication authentication) {

        User user = authenticatedUser(authentication);
        List<File> files = fileService.searchUserFiles(user.getId(), query);
        List<FileResponse> response = files.stream()
                .map(FileResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<StorageStatsResponse> getStorageStats(Authentication authentication) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(fileService.getStorageStats(user.getId()));
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<FileResponse> getFileMetadata(
            @PathVariable Long fileId,
            Authentication authentication) {

        User user = authenticatedUser(authentication);
        File file = fileService.getUserFile(fileId, user.getId());
        return ResponseEntity.ok(FileResponse.fromEntity(file));
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long fileId,
            Authentication authentication) throws MalformedURLException {

        User user = authenticatedUser(authentication);
        File file = fileService.getUserFile(fileId, user.getId());
        Path path = fileStorageService.getFilePath(user.getId(), file.getStoredFilename());

        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Physical file not found on disk");
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
                                .filename(file.getFilename(), StandardCharsets.UTF_8)
                                .build().toString())
                .contentLength(file.getSize())
                .body(resource);
    }

    @PutMapping("/{fileId}")
    public ResponseEntity<FileResponse> renameFile(
            @PathVariable Long fileId,
            @RequestParam("filename") String filename,
            Authentication authentication) {

        User user = authenticatedUser(authentication);
        File updated = fileService.renameFile(fileId, user.getId(), filename);
        return ResponseEntity.ok(FileResponse.fromEntity(updated));
    }

    @PutMapping("/{fileId}/move")
    public ResponseEntity<FileResponse> moveFile(
            @PathVariable Long fileId,
            @RequestParam(value = "folderId", required = false) Long folderId,
            Authentication authentication) {

        User user = authenticatedUser(authentication);
        File moved = fileService.moveFile(fileId, user.getId(), folderId);
        return ResponseEntity.ok(FileResponse.fromEntity(moved));
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Map<String, String>> deleteFile(
            @PathVariable Long fileId,
            Authentication authentication) {

        User user = authenticatedUser(authentication);
        fileService.deleteFile(fileId, user.getId());
        return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
    }

    private User authenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Authentication required");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
    }
}
