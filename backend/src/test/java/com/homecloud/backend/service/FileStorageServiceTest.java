package com.homecloud.backend.service;

import com.homecloud.backend.exception.BadRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    @TempDir
    Path tempStorageDir;

    private FileStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new FileStorageService(tempStorageDir.toString());
    }

    @Test
    @DisplayName("Store file creates file inside user directory")
    void storeFile_storesInUserDirectory() throws IOException {
        Long userId = 101L;
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "Sample PDF content".getBytes()
        );

        String storedFilename = storageService.storeFile(file, userId);
        assertNotNull(storedFilename);
        assertTrue(storedFilename.endsWith("_document.pdf"));

        Path resolvedPath = storageService.getFilePath(userId, storedFilename);
        assertTrue(Files.exists(resolvedPath));
        assertTrue(resolvedPath.startsWith(tempStorageDir.resolve("users").resolve(String.valueOf(userId))));
        assertEquals("Sample PDF content", Files.readString(resolvedPath));
    }

    @Test
    @DisplayName("Path traversal attempts in filenames are blocked and sanitized")
    void storeFile_pathTraversalSanitized() {
        Long userId = 102L;
        MockMultipartFile maliciousFile = new MockMultipartFile(
                "file",
                "../../../../evil.sh",
                "text/x-shellscript",
                "echo bad".getBytes()
        );

        String storedFilename = storageService.storeFile(maliciousFile, userId);
        assertNotNull(storedFilename);
        // The base name evil.sh should be kept, directory traversal components removed
        assertTrue(storedFilename.endsWith("_evil.sh"));

        Path path = storageService.getFilePath(userId, storedFilename);
        assertTrue(path.startsWith(storageService.getUserDirectory(userId)));
    }

    @Test
    @DisplayName("Empty file throws BadRequestException")
    void storeFile_emptyFile_throwsBadRequest() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        assertThrows(BadRequestException.class, () ->
                storageService.storeFile(emptyFile, 1L));
    }

    @Test
    @DisplayName("Windows reserved device names are prefixed safely")
    void sanitizeFilename_windowsReserved() {
        String sanitized = storageService.sanitizeFilename("con.txt");
        assertEquals("safe_con.txt", sanitized);

        String sanitizedAux = storageService.sanitizeFilename("AUX");
        assertEquals("safe_AUX", sanitizedAux);
    }

    @Test
    @DisplayName("Deleting file removes it from disk")
    void deleteFile_removesFile() {
        Long userId = 103L;
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "temp.txt",
                "text/plain",
                "to be deleted".getBytes()
        );

        String storedFilename = storageService.storeFile(file, userId);
        Path path = storageService.getFilePath(userId, storedFilename);
        assertTrue(Files.exists(path));

        storageService.deleteFile(userId, storedFilename);
        assertFalse(Files.exists(path));
    }
}
