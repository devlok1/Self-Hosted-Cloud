package com.homecloud.backend.service;

import com.homecloud.backend.dto.FileResponse;
import com.homecloud.backend.dto.InitiateTransferRequest;
import com.homecloud.backend.dto.TransferChunkResponse;
import com.homecloud.backend.dto.TransferSessionResponse;
import com.homecloud.backend.dto.TransferStatusResponse;
import com.homecloud.backend.entity.*;
import com.homecloud.backend.exception.BadRequestException;
import com.homecloud.backend.exception.ConflictException;
import com.homecloud.backend.repository.FileRepository;
import com.homecloud.backend.repository.UploadChunkRepository;
import com.homecloud.backend.repository.UploadSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private UploadSessionRepository uploadSessionRepository;
    @Mock
    private UploadChunkRepository uploadChunkRepository;
    @Mock
    private FileRepository fileRepository;
    @Mock
    private FolderService folderService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private TransferStorageService transferStorageService;

    private TransferService transferService;
    private User testUser;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(
                uploadSessionRepository,
                uploadChunkRepository,
                fileRepository,
                folderService,
                fileStorageService,
                transferStorageService,
                10737418240L, // 10 GB max
                1048576L,     // 1 MB default
                1024L,        // 1 KB min
                5242880L,     // 5 MB max
                5,            // 5 max active sessions
                24            // 24h expiration
        );

        testUser = new User("test@homecloud.local", "hashedPassword");
        testUser.setId(1L);
    }

    @Test
    void testInitiateSessionSuccess() {
        InitiateTransferRequest req = new InitiateTransferRequest("data.zip", 2097152L, 1048576L, null, "application/zip", "expectedSha256");

        when(fileStorageService.sanitizeFilename("data.zip")).thenReturn("data.zip");
        when(uploadSessionRepository.countByOwner_IdAndStatusIn(eq(1L), anyCollection())).thenReturn(0L);
        when(uploadSessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferSessionResponse response = transferService.initiateSession(testUser, req);

        assertNotNull(response.getUploadId());
        assertEquals("data.zip", response.getFilename());
        assertEquals(2097152L, response.getFileSize());
        assertEquals(1048576L, response.getChunkSize());
        assertEquals(2, response.getTotalChunks());
        assertEquals(UploadSessionStatus.INITIATED, response.getStatus());
    }

    @Test
    void testInitiateSessionRejectsExcessiveConcurrentSessions() {
        InitiateTransferRequest req = new InitiateTransferRequest("data.zip", 1000L, 1000L, null, "text/plain", null);

        when(fileStorageService.sanitizeFilename("data.zip")).thenReturn("data.zip");
        when(uploadSessionRepository.countByOwner_IdAndStatusIn(eq(1L), anyCollection())).thenReturn(5L);

        assertThrows(ConflictException.class, () -> transferService.initiateSession(testUser, req));
    }

    @Test
    void testUploadChunkSuccess() {
        String uploadId = "test-session";
        UploadSession session = new UploadSession(uploadId, testUser, null, "file.txt", "text/plain",
                2000L, 1000L, 2, null, LocalDateTime.now().plusHours(1));

        when(uploadSessionRepository.findByIdAndOwner_Id(uploadId, 1L)).thenReturn(Optional.of(session));
        when(uploadChunkRepository.findBySession_IdAndChunkNumber(uploadId, 0)).thenReturn(Optional.empty());
        when(transferStorageService.storeChunk(eq(1L), eq(uploadId), eq(0), any(InputStream.class), eq(1000L)))
                .thenReturn(new TransferStorageService.ChunkWriteResult(1000L, "mockChecksum"));
        when(uploadChunkRepository.countBySession_Id(uploadId)).thenReturn(1L);

        MockMultipartFile chunkFile = new MockMultipartFile("chunk", "chunk.part", "application/octet-stream", new byte[1000]);

        TransferChunkResponse response = transferService.uploadChunk(uploadId, 0, chunkFile, "mockChecksum", testUser);

        assertEquals(uploadId, response.getUploadId());
        assertEquals(0, response.getChunkNumber());
        assertEquals(1000L, response.getSize());
        assertEquals("mockChecksum", response.getChecksum());
        assertEquals(1, response.getUploadedChunksCount());
        verify(uploadChunkRepository).save(any(UploadChunk.class));
    }

    @Test
    void testUploadChunkChecksumMismatchThrowsBadRequest() {
        String uploadId = "test-session";
        UploadSession session = new UploadSession(uploadId, testUser, null, "file.txt", "text/plain",
                1000L, 1000L, 1, null, LocalDateTime.now().plusHours(1));

        when(uploadSessionRepository.findByIdAndOwner_Id(uploadId, 1L)).thenReturn(Optional.of(session));
        when(uploadChunkRepository.findBySession_IdAndChunkNumber(uploadId, 0)).thenReturn(Optional.empty());
        when(transferStorageService.storeChunk(eq(1L), eq(uploadId), eq(0), any(InputStream.class), eq(1000L)))
                .thenReturn(new TransferStorageService.ChunkWriteResult(1000L, "actualChecksum"));

        MockMultipartFile chunkFile = new MockMultipartFile("chunk", "chunk.part", "application/octet-stream", new byte[1000]);

        assertThrows(BadRequestException.class, () ->
                transferService.uploadChunk(uploadId, 0, chunkFile, "wrongExpectedChecksum", testUser)
        );
    }

    @Test
    void testCompleteSessionSuccess() {
        String uploadId = "test-session";
        UploadSession session = new UploadSession(uploadId, testUser, null, "photo.jpg", "image/jpeg",
                2000L, 1000L, 2, "validFinalSha256", LocalDateTime.now().plusHours(1));

        UploadChunk chunk0 = new UploadChunk(session, 0, 1000L, "chk0");
        UploadChunk chunk1 = new UploadChunk(session, 1, 1000L, "chk1");

        when(uploadSessionRepository.findByIdAndOwner_Id(uploadId, 1L)).thenReturn(Optional.of(session));
        when(uploadChunkRepository.findBySession_IdOrderByChunkNumberAsc(uploadId)).thenReturn(List.of(chunk0, chunk1));
        when(transferStorageService.assembleChunks(1L, uploadId, 2, 2000L))
                .thenReturn(new TransferStorageService.AssemblyResult(Paths.get("temp/assemble.tmp"), 2000L, "validFinalSha256"));
        when(fileStorageService.getFilePath(eq(1L), anyString())).thenReturn(Paths.get("HomeCloudStorage/users/1/stored.jpg"));

        File savedEntity = new File("photo.jpg", "stored.jpg", "path", 2000L, "image/jpeg", testUser);
        when(fileRepository.save(any(File.class))).thenReturn(savedEntity);

        FileResponse response = transferService.completeSession(uploadId, testUser);

        assertNotNull(response);
        assertEquals("photo.jpg", response.getFilename());
        assertEquals(2000L, response.getSize());
        assertEquals(UploadSessionStatus.COMPLETED, session.getStatus());
        verify(transferStorageService).deleteTransferDirectory(1L, uploadId);
    }

    @Test
    void testCompleteSessionMissingChunksThrowsBadRequest() {
        String uploadId = "test-session";
        UploadSession session = new UploadSession(uploadId, testUser, null, "photo.jpg", "image/jpeg",
                2000L, 1000L, 2, null, LocalDateTime.now().plusHours(1));

        UploadChunk chunk0 = new UploadChunk(session, 0, 1000L, "chk0");
        // Chunk 1 is missing
        when(uploadSessionRepository.findByIdAndOwner_Id(uploadId, 1L)).thenReturn(Optional.of(session));
        when(uploadChunkRepository.findBySession_IdOrderByChunkNumberAsc(uploadId)).thenReturn(List.of(chunk0));

        assertThrows(BadRequestException.class, () -> transferService.completeSession(uploadId, testUser));
    }

    @Test
    void testCancelSessionCleansUp() {
        String uploadId = "test-session";
        UploadSession session = new UploadSession(uploadId, testUser, null, "photo.jpg", "image/jpeg",
                2000L, 1000L, 2, null, LocalDateTime.now().plusHours(1));

        when(uploadSessionRepository.findByIdAndOwner_Id(uploadId, 1L)).thenReturn(Optional.of(session));

        transferService.cancelSession(uploadId, testUser);

        assertEquals(UploadSessionStatus.CANCELLED, session.getStatus());
        verify(uploadChunkRepository).deleteBySession_Id(uploadId);
        verify(transferStorageService).deleteTransferDirectory(1L, uploadId);
    }

    @Test
    void testGetSessionStatus() {
        String uploadId = "test-session";
        UploadSession session = new UploadSession(uploadId, testUser, null, "photo.jpg", "image/jpeg",
                3000L, 1000L, 3, null, LocalDateTime.now().plusHours(1));

        UploadChunk chunk0 = new UploadChunk(session, 0, 1000L, "chk0");
        UploadChunk chunk2 = new UploadChunk(session, 2, 1000L, "chk2");

        when(uploadSessionRepository.findByIdAndOwner_Id(uploadId, 1L)).thenReturn(Optional.of(session));
        when(uploadChunkRepository.findBySession_IdOrderByChunkNumberAsc(uploadId)).thenReturn(List.of(chunk0, chunk2));

        TransferStatusResponse response = transferService.getSessionStatus(uploadId, testUser);

        assertEquals(uploadId, response.getUploadId());
        assertEquals(List.of(0, 2), response.getUploadedChunks());
        assertEquals(2, response.getUploadedChunksCount());
        assertEquals(3, response.getTotalChunks());
    }
}
