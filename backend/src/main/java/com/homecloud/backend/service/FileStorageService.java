package com.homecloud.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path storageLocation;

    public FileStorageService(
            @Value("${homecloud.storage.location:HomeCloudStorage}") String storageDirectory)
            throws IOException {

        storageLocation = Paths.get(storageDirectory)
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(storageLocation);
    }

    public String storeFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IOException("Invalid file name");
        }

        String cleanFilename = Paths.get(originalFilename)
                .getFileName()
                .toString();

        String storedFilename = UUID.randomUUID() + "_" + cleanFilename;
        Path targetLocation = resolveSafe(storedFilename);

        Files.copy(file.getInputStream(), targetLocation,
                StandardCopyOption.REPLACE_EXISTING);

        return storedFilename;
    }

    public Path getFilePath(String storedFilename) {
        return resolveSafe(storedFilename);
    }

    public void deleteFile(String storedFilename) throws IOException {
        Files.deleteIfExists(resolveSafe(storedFilename));
    }

    private Path resolveSafe(String filename) {
        Path path = storageLocation.resolve(filename).normalize();
        if (!path.startsWith(storageLocation)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        return path;
    }
}
