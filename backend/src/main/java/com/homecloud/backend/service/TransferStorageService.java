package com.homecloud.backend.service;

import com.homecloud.backend.exception.BadRequestException;
import com.homecloud.backend.exception.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Service
public class TransferStorageService {

    private static final Logger log = LoggerFactory.getLogger(TransferStorageService.class);
    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");
    private static final int BUFFER_SIZE = 1024 * 1024; // 1 MB buffer for streaming

    private final FileStorageService fileStorageService;

    public TransferStorageService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public Path getTransferDirectory(Long userId, String uploadId) {
        validateIds(userId, uploadId);
        Path userDir = fileStorageService.getUserDirectory(userId);
        Path transfersDir = resolveSafe(userDir, "transfers");
        return resolveSafe(transfersDir, uploadId);
    }

    public Path getChunkPath(Long userId, String uploadId, int chunkNumber) {
        if (chunkNumber < 0) {
            throw new BadRequestException("Invalid chunk number: " + chunkNumber);
        }
        Path uploadDir = getTransferDirectory(userId, uploadId);
        return resolveSafe(uploadDir, "chunk_" + chunkNumber + ".part");
    }

    public Path getAssemblyPath(Long userId, String uploadId) {
        validateIds(userId, uploadId);
        Path userDir = fileStorageService.getUserDirectory(userId);
        Path transfersDir = resolveSafe(userDir, "transfers");
        return resolveSafe(transfersDir, "assemble_" + uploadId + ".tmp");
    }

    public ChunkWriteResult storeChunk(Long userId, String uploadId, int chunkNumber,
                                       InputStream inputStream, long expectedSize) {
        Path chunkFinalPath = getChunkPath(userId, uploadId, chunkNumber);
        Path uploadDir = chunkFinalPath.getParent();
        Path tempPath = resolveSafe(uploadDir, "chunk_" + chunkNumber + ".tmp");

        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new StorageException("Could not create transfer directory: " + uploadDir, e);
        }

        checkAvailableDiskSpace(uploadDir, expectedSize);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long bytesWritten = 0;

            try (DigestInputStream dis = new DigestInputStream(inputStream, digest);
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempPath,
                         StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = dis.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    bytesWritten += read;
                }
                out.flush();
            }

            if (expectedSize > 0 && bytesWritten != expectedSize) {
                Files.deleteIfExists(tempPath);
                throw new BadRequestException("Chunk size mismatch. Expected: " + expectedSize + " bytes, received: " + bytesWritten + " bytes");
            }

            String checksum = HexFormat.of().formatHex(digest.digest());

            try {
                Files.move(tempPath, chunkFinalPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, chunkFinalPath, StandardCopyOption.REPLACE_EXISTING);
            }

            return new ChunkWriteResult(bytesWritten, checksum);

        } catch (NoSuchAlgorithmException e) {
            throw new StorageException("SHA-256 algorithm not available", e);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {}
            throw new StorageException("Failed to write chunk " + chunkNumber + " for upload " + uploadId, e);
        }
    }

    public boolean chunkExists(Long userId, String uploadId, int chunkNumber, long expectedSize) {
        Path path = getChunkPath(userId, uploadId, chunkNumber);
        try {
            return Files.exists(path) && Files.size(path) == expectedSize;
        } catch (IOException e) {
            return false;
        }
    }

    public AssemblyResult assembleChunks(Long userId, String uploadId, int totalChunks, long expectedTotalSize) {
        Path assemblyPath = getAssemblyPath(userId, uploadId);
        checkAvailableDiskSpace(assemblyPath.getParent(), expectedTotalSize);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long totalBytesWritten = 0;

            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(assemblyPath,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))) {

                byte[] buffer = new byte[BUFFER_SIZE];

                for (int i = 0; i < totalChunks; i++) {
                    Path chunkPath = getChunkPath(userId, uploadId, i);
                    if (!Files.exists(chunkPath)) {
                        throw new BadRequestException("Missing chunk " + i + " during assembly");
                    }

                    try (InputStream in = new BufferedInputStream(Files.newInputStream(chunkPath))) {
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                            digest.update(buffer, 0, read);
                            totalBytesWritten += read;
                        }
                    }
                }
                out.flush();
            }

            if (totalBytesWritten != expectedTotalSize) {
                Files.deleteIfExists(assemblyPath);
                throw new BadRequestException("Assembled file size mismatch. Expected: "
                        + expectedTotalSize + " bytes, assembled: " + totalBytesWritten + " bytes");
            }

            String finalChecksum = HexFormat.of().formatHex(digest.digest());
            return new AssemblyResult(assemblyPath, totalBytesWritten, finalChecksum);

        } catch (NoSuchAlgorithmException e) {
            throw new StorageException("SHA-256 algorithm not available", e);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(assemblyPath);
            } catch (IOException ignored) {}
            throw new StorageException("Failed to assemble chunks for upload: " + uploadId, e);
        }
    }

    public void moveAssemblyToFinalLocation(Path assemblyPath, Long userId, String storedFilename) {
        Path userDir = fileStorageService.getUserDirectory(userId);
        Path targetPath = resolveSafe(userDir, storedFilename);

        try {
            try {
                Files.move(assemblyPath, targetPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(assemblyPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(assemblyPath);
                Files.deleteIfExists(targetPath);
            } catch (IOException ignored) {}
            throw new StorageException("Failed to move assembled file to final storage: " + targetPath, e);
        }
    }

    public void deleteTransferDirectory(Long userId, String uploadId) {
        validateIds(userId, uploadId);
        Path userDir = fileStorageService.getUserDirectory(userId);
        Path transfersDir = resolveSafe(userDir, "transfers");
        Path uploadDir = resolveSafe(transfersDir, uploadId);
        Path assemblyPath = resolveSafe(transfersDir, "assemble_" + uploadId + ".tmp");

        try {
            Files.deleteIfExists(assemblyPath);
        } catch (IOException e) {
            log.warn("Failed to delete assembly file: {}", assemblyPath, e);
        }

        if (Files.exists(uploadDir)) {
            try {
                java.util.List<Path> paths;
                try (var stream = Files.walk(uploadDir)) {
                    paths = stream.sorted(Comparator.reverseOrder()).toList();
                }
                for (Path p : paths) {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {}
                }
            } catch (IOException e) {
                log.warn("Failed to clean up transfer directory: {}", uploadDir, e);
            }
        }
    }

    public void checkAvailableDiskSpace(Path dir, long bytesNeeded) {
        try {
            FileStore store = Files.getFileStore(dir);
            long usableSpace = store.getUsableSpace();
            // Maintain at least 50 MB safety margin on disk
            long safetyMargin = 50L * 1024L * 1024L;
            if (usableSpace < (bytesNeeded + safetyMargin)) {
                throw new StorageException("Insufficient disk space. Available: "
                        + (usableSpace / (1024 * 1024)) + " MB, needed: "
                        + ((bytesNeeded + safetyMargin) / (1024 * 1024)) + " MB");
            }
        } catch (IOException e) {
            log.warn("Could not check usable disk space for directory: {}", dir, e);
        }
    }

    private void validateIds(Long userId, String uploadId) {
        if (userId == null) {
            throw new BadRequestException("User ID must not be null");
        }
        if (uploadId == null || !SAFE_ID_PATTERN.matcher(uploadId).matches()) {
            throw new BadRequestException("Invalid upload session ID format");
        }
    }

    private Path resolveSafe(Path baseDir, String filename) {
        Path resolved = baseDir.resolve(filename).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new BadRequestException("Path traversal attempt detected in transfer storage");
        }
        return resolved;
    }

    public record ChunkWriteResult(long size, String checksum) {}
    public record AssemblyResult(Path path, long totalSize, String checksum) {}
}
