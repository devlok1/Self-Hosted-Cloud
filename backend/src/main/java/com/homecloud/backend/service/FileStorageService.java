package com.homecloud.backend.service;

import com.homecloud.backend.exception.BadRequestException;
import com.homecloud.backend.exception.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1F\\x7F]");
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    private final Path rootStorageLocation;

    public FileStorageService(
            @Value("${homecloud.storage.location:HomeCloudStorage}") String storageDirectory) {

        this.rootStorageLocation = Paths.get(storageDirectory)
                .toAbsolutePath()
                .normalize();

        try {
            Files.createDirectories(this.rootStorageLocation);
        } catch (IOException e) {
            throw new StorageException("Could not initialize root storage directory: " + this.rootStorageLocation, e);
        }
    }

    public Path getRootStorageLocation() {
        return rootStorageLocation;
    }

    public String storeFile(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file is empty");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        String originalFilename = file.getOriginalFilename();
        String cleanFilename = sanitizeFilename(originalFilename);

        Path userDir = getUserDirectory(userId);
        String storedFilename = UUID.randomUUID() + "_" + cleanFilename;
        Path targetLocation = resolveSafe(userDir, storedFilename);

        // Safe temporary staging to prevent partial files lingering on upload abort
        String tempFilename = UUID.randomUUID() + ".tmp";
        Path tempPath = resolveSafe(userDir, tempFilename);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, tempPath, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(tempPath, targetLocation, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }
            return storedFilename;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempPath);
                Files.deleteIfExists(targetLocation);
            } catch (IOException ignored) {}
            throw new StorageException("Failed to store file: " + cleanFilename, e);
        }
    }

    public Path getFilePath(Long userId, String storedFilename) {
        if (userId == null || storedFilename == null || storedFilename.isBlank()) {
            throw new BadRequestException("Invalid file identifier");
        }

        Path userDir = getUserDirectory(userId);
        Path userPath = resolveSafe(userDir, storedFilename);

        if (Files.exists(userPath)) {
            return userPath;
        }

        // Backward compatibility: check if file was created before user partitioning
        Path legacyPath = resolveSafe(rootStorageLocation, storedFilename);
        if (Files.exists(legacyPath)) {
            return legacyPath;
        }

        return userPath;
    }

    public void deleteFile(Long userId, String storedFilename) {
        if (userId == null || storedFilename == null || storedFilename.isBlank()) {
            return;
        }

        try {
            Path path = getFilePath(userId, storedFilename);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("Failed to delete stored file {} for user {}: {}", storedFilename, userId, e.getMessage());
            throw new StorageException("Failed to delete file from storage", e);
        }
    }

    public Path getUserDirectory(Long userId) {
        Path userDir = rootStorageLocation.resolve("users").resolve(String.valueOf(userId)).normalize();
        if (!userDir.startsWith(rootStorageLocation)) {
            throw new BadRequestException("Invalid user directory path");
        }
        try {
            Files.createDirectories(userDir);
        } catch (IOException e) {
            throw new StorageException("Could not create user directory: " + userDir, e);
        }
        return userDir;
    }

    public String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new BadRequestException("File name cannot be empty");
        }

        String base = Paths.get(filename.trim()).getFileName().toString();
        String sanitized = CONTROL_CHARS.matcher(base).replaceAll("").trim();

        if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")) {
            throw new BadRequestException("Invalid file name");
        }

        if (sanitized.length() > 255) {
            throw new BadRequestException("File name exceeds maximum allowed length of 255 characters");
        }

        String nameWithoutExt = sanitized.contains(".")
                ? sanitized.substring(0, sanitized.lastIndexOf('.'))
                : sanitized;

        if (WINDOWS_RESERVED_NAMES.contains(nameWithoutExt.toUpperCase())) {
            sanitized = "safe_" + sanitized;
        }

        return sanitized;
    }

    private Path resolveSafe(Path baseDir, String filename) {
        Path resolved = baseDir.resolve(filename).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new BadRequestException("Path traversal attempt detected");
        }
        return resolved;
    }
}
