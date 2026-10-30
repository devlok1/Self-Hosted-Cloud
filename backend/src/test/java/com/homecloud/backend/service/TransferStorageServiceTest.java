package com.homecloud.backend.service;

import com.homecloud.backend.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class TransferStorageServiceTest {

    private TransferStorageService transferStorageService;

    @TempDir
    Path tempStorageDir;

    @BeforeEach
    void setUp() {
        FileStorageService fileStorageService = new FileStorageService(tempStorageDir.toString());
        transferStorageService = new TransferStorageService(fileStorageService);
    }

    @Test
    void testStoreChunkAndAssemble() throws IOException, NoSuchAlgorithmException {
        Long userId = 1L;
        String uploadId = "test-upload-session-1";

        byte[] chunk0Bytes = "First chunk of content. ".getBytes(StandardCharsets.UTF_8);
        byte[] chunk1Bytes = "Second chunk of content.".getBytes(StandardCharsets.UTF_8);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String chunk0ExpectedChecksum = HexFormat.of().formatHex(digest.digest(chunk0Bytes));

        digest.reset();
        String chunk1ExpectedChecksum = HexFormat.of().formatHex(digest.digest(chunk1Bytes));

        digest.reset();
        byte[] totalBytes = "First chunk of content. Second chunk of content.".getBytes(StandardCharsets.UTF_8);
        String finalExpectedChecksum = HexFormat.of().formatHex(digest.digest(totalBytes));

        // Store chunk 0
        TransferStorageService.ChunkWriteResult res0 = transferStorageService.storeChunk(
                userId, uploadId, 0, new ByteArrayInputStream(chunk0Bytes), chunk0Bytes.length
        );
        assertEquals(chunk0Bytes.length, res0.size());
        assertEquals(chunk0ExpectedChecksum, res0.checksum());
        assertTrue(transferStorageService.chunkExists(userId, uploadId, 0, chunk0Bytes.length));

        // Store chunk 1
        TransferStorageService.ChunkWriteResult res1 = transferStorageService.storeChunk(
                userId, uploadId, 1, new ByteArrayInputStream(chunk1Bytes), chunk1Bytes.length
        );
        assertEquals(chunk1Bytes.length, res1.size());
        assertEquals(chunk1ExpectedChecksum, res1.checksum());
        assertTrue(transferStorageService.chunkExists(userId, uploadId, 1, chunk1Bytes.length));

        // Assemble chunks
        TransferStorageService.AssemblyResult assembly = transferStorageService.assembleChunks(
                userId, uploadId, 2, totalBytes.length
        );
        assertEquals(totalBytes.length, assembly.totalSize());
        assertEquals(finalExpectedChecksum, assembly.checksum());
        assertTrue(Files.exists(assembly.path()));

        // Move to final location
        String storedFilename = "final_assembled_file.txt";
        transferStorageService.moveAssemblyToFinalLocation(assembly.path(), userId, storedFilename);

        Path finalPath = tempStorageDir.resolve("users").resolve("1").resolve(storedFilename);
        assertTrue(Files.exists(finalPath));
        assertEquals(new String(totalBytes, StandardCharsets.UTF_8), Files.readString(finalPath));

        // Cleanup
        transferStorageService.deleteTransferDirectory(userId, uploadId);
        assertFalse(Files.exists(transferStorageService.getTransferDirectory(userId, uploadId)));
    }

    @Test
    void testStoreChunkSizeMismatchThrowsException() {
        Long userId = 1L;
        String uploadId = "mismatch-upload";
        byte[] data = "Short content".getBytes(StandardCharsets.UTF_8);

        assertThrows(BadRequestException.class, () ->
                transferStorageService.storeChunk(userId, uploadId, 0, new ByteArrayInputStream(data), 1000L)
        );
    }

    @Test
    void testPathTraversalRejected() {
        assertThrows(BadRequestException.class, () ->
                transferStorageService.getTransferDirectory(1L, "../../../etc/passwd")
        );
    }
}
