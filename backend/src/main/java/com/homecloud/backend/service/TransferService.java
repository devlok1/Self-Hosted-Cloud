package com.homecloud.backend.service;

import com.homecloud.backend.dto.*;
import com.homecloud.backend.entity.*;
import com.homecloud.backend.exception.BadRequestException;
import com.homecloud.backend.exception.ConflictException;
import com.homecloud.backend.exception.ResourceNotFoundException;
import com.homecloud.backend.repository.FileRepository;
import com.homecloud.backend.repository.UploadChunkRepository;
import com.homecloud.backend.repository.UploadSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final UploadSessionRepository uploadSessionRepository;
    private final UploadChunkRepository uploadChunkRepository;
    private final FileRepository fileRepository;
    private final FolderService folderService;
    private final FileStorageService fileStorageService;
    private final TransferStorageService transferStorageService;

    private final long maxFileSize;
    private final long defaultChunkSize;
    private final long minChunkSize;
    private final long maxChunkSize;
    private final int maxActiveSessionsPerUser;
    private final int sessionExpirationHours;

    public TransferService(
            UploadSessionRepository uploadSessionRepository,
            UploadChunkRepository uploadChunkRepository,
            FileRepository fileRepository,
            FolderService folderService,
            FileStorageService fileStorageService,
            TransferStorageService transferStorageService,
            @Value("${homecloud.transfers.max-file-size:53687091200}") long maxFileSize,
            @Value("${homecloud.transfers.default-chunk-size:5242880}") long defaultChunkSize,
            @Value("${homecloud.transfers.min-chunk-size:1048576}") long minChunkSize,
            @Value("${homecloud.transfers.max-chunk-size:20971520}") long maxChunkSize,
            @Value("${homecloud.transfers.max-active-sessions-per-user:5}") int maxActiveSessionsPerUser,
            @Value("${homecloud.transfers.session-expiration-hours:24}") int sessionExpirationHours) {

        this.uploadSessionRepository = uploadSessionRepository;
        this.uploadChunkRepository = uploadChunkRepository;
        this.fileRepository = fileRepository;
        this.folderService = folderService;
        this.fileStorageService = fileStorageService;
        this.transferStorageService = transferStorageService;

        this.maxFileSize = maxFileSize;
        this.defaultChunkSize = defaultChunkSize;
        this.minChunkSize = minChunkSize;
        this.maxChunkSize = maxChunkSize;
        this.maxActiveSessionsPerUser = maxActiveSessionsPerUser;
        this.sessionExpirationHours = sessionExpirationHours;
    }

    public TransferSessionResponse initiateSession(User user, InitiateTransferRequest request) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        if (request == null) {
            throw new BadRequestException("Request body cannot be null");
        }

        String rawFilename = request.getFilename();
        if (rawFilename == null || rawFilename.isBlank()) {
            throw new BadRequestException("Filename cannot be empty");
        }
        String cleanFilename = fileStorageService.sanitizeFilename(rawFilename);

        Long fileSize = request.getFileSize();
        if (fileSize == null || fileSize <= 0) {
            throw new BadRequestException("File size must be greater than 0");
        }
        if (fileSize > maxFileSize) {
            throw new BadRequestException("File size exceeds maximum allowed size of " + (maxFileSize / (1024 * 1024 * 1024)) + " GB");
        }

        long activeSessions = uploadSessionRepository.countByOwner_IdAndStatusIn(
                user.getId(),
                List.of(UploadSessionStatus.INITIATED, UploadSessionStatus.UPLOADING, UploadSessionStatus.FINALIZING)
        );
        if (activeSessions >= maxActiveSessionsPerUser) {
            throw new ConflictException("Maximum active upload sessions (" + maxActiveSessionsPerUser
                    + ") reached for this user. Please complete or cancel existing sessions.");
        }

        long chunkSize = request.getChunkSize() != null && request.getChunkSize() > 0
                ? request.getChunkSize()
                : defaultChunkSize;

        long effectiveMinChunk = fileSize < minChunkSize ? 1L : minChunkSize;
        if (chunkSize < effectiveMinChunk || chunkSize > maxChunkSize) {
            throw new BadRequestException("Chunk size must be between "
                    + effectiveMinChunk + " bytes and "
                    + maxChunkSize + " bytes");
        }

        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);

        Folder folder = null;
        if (request.getFolderId() != null) {
            folder = folderService.getFolder(request.getFolderId(), user.getId());
        }

        String uploadId = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(sessionExpirationHours);

        UploadSession session = new UploadSession(
                uploadId,
                user,
                folder,
                cleanFilename,
                request.getContentType(),
                fileSize,
                chunkSize,
                totalChunks,
                request.getExpectedChecksum(),
                expiresAt
        );

        UploadSession saved = uploadSessionRepository.save(session);
        return TransferSessionResponse.fromEntity(saved, Collections.emptyList());
    }

    public TransferChunkResponse uploadChunk(String uploadId, int chunkNumber, MultipartFile chunkFile,
                                            String clientChecksum, User user) {
        if (chunkFile == null || chunkFile.isEmpty()) {
            throw new BadRequestException("Chunk payload cannot be empty");
        }

        UploadSession session = getActiveSession(uploadId, user.getId());

        if (chunkNumber < 0 || chunkNumber >= session.getTotalChunks()) {
            throw new BadRequestException("Invalid chunk index: " + chunkNumber
                    + ". Expected between 0 and " + (session.getTotalChunks() - 1));
        }

        long expectedSize = calculateExpectedChunkSize(session, chunkNumber);
        if (chunkFile.getSize() != expectedSize) {
            throw new BadRequestException("Invalid chunk size for chunk " + chunkNumber
                    + ". Expected: " + expectedSize + " bytes, received: " + chunkFile.getSize() + " bytes");
        }

        // Idempotency check: if chunk already exists and matches expected size
        Optional<UploadChunk> existingChunk = uploadChunkRepository.findBySession_IdAndChunkNumber(uploadId, chunkNumber);
        if (existingChunk.isPresent() && transferStorageService.chunkExists(user.getId(), uploadId, chunkNumber, expectedSize)) {
            UploadChunk chunk = existingChunk.get();
            if (clientChecksum == null || clientChecksum.isBlank() || chunk.getChecksum().equalsIgnoreCase(clientChecksum)) {
                long count = uploadChunkRepository.countBySession_Id(uploadId);
                return new TransferChunkResponse(
                        uploadId,
                        chunkNumber,
                        chunk.getSize(),
                        chunk.getChecksum(),
                        (int) count,
                        session.getTotalChunks(),
                        session.getStatus()
                );
            }
        }

        TransferStorageService.ChunkWriteResult writeResult;
        try (InputStream in = chunkFile.getInputStream()) {
            writeResult = transferStorageService.storeChunk(user.getId(), uploadId, chunkNumber, in, expectedSize);
        } catch (IOException e) {
            throw new BadRequestException("Failed to read chunk payload", e);
        }

        if (clientChecksum != null && !clientChecksum.isBlank()) {
            if (!writeResult.checksum().equalsIgnoreCase(clientChecksum.trim())) {
                throw new BadRequestException("Chunk checksum mismatch. Expected: "
                        + clientChecksum + ", computed: " + writeResult.checksum());
            }
        }

        // Save chunk metadata
        UploadChunk chunkEntity = existingChunk.orElseGet(() -> new UploadChunk(session, chunkNumber, writeResult.size(), writeResult.checksum()));
        chunkEntity.setSize(writeResult.size());
        chunkEntity.setChecksum(writeResult.checksum());
        chunkEntity.setUploadedAt(LocalDateTime.now());
        uploadChunkRepository.save(chunkEntity);

        if (session.getStatus() == UploadSessionStatus.INITIATED) {
            session.setStatus(UploadSessionStatus.UPLOADING);
            uploadSessionRepository.save(session);
        }

        long count = uploadChunkRepository.countBySession_Id(uploadId);
        return new TransferChunkResponse(
                uploadId,
                chunkNumber,
                writeResult.size(),
                writeResult.checksum(),
                (int) count,
                session.getTotalChunks(),
                session.getStatus()
        );
    }

    @Transactional(readOnly = true)
    public TransferStatusResponse getSessionStatus(String uploadId, User user) {
        UploadSession session = uploadSessionRepository.findByIdAndOwner_Id(uploadId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Transfer session not found: " + uploadId));

        List<Integer> uploadedChunks = uploadChunkRepository.findBySession_IdOrderByChunkNumberAsc(uploadId)
                .stream()
                .map(UploadChunk::getChunkNumber)
                .toList();

        return TransferStatusResponse.fromEntity(session, uploadedChunks);
    }

    public FileResponse completeSession(String uploadId, User user) {
        UploadSession session = getActiveSession(uploadId, user.getId());

        List<UploadChunk> chunks = uploadChunkRepository.findBySession_IdOrderByChunkNumberAsc(uploadId);
        if (chunks.size() != session.getTotalChunks()) {
            throw new BadRequestException("Cannot finalize transfer. Missing chunks. Received "
                    + chunks.size() + " of " + session.getTotalChunks() + " required chunks.");
        }

        // Transition to FINALIZING
        session.setStatus(UploadSessionStatus.FINALIZING);
        uploadSessionRepository.save(session);

        // Stream assembly outside the database transaction
        TransferStorageService.AssemblyResult assembly;
        try {
            assembly = transferStorageService.assembleChunks(
                    user.getId(),
                    uploadId,
                    session.getTotalChunks(),
                    session.getFileSize()
            );
        } catch (Exception e) {
            session.setStatus(UploadSessionStatus.FAILED);
            uploadSessionRepository.save(session);
            transferStorageService.deleteTransferDirectory(user.getId(), uploadId);
            throw e;
        }

        // Checksum verification
        if (session.getExpectedChecksum() != null && !session.getExpectedChecksum().isBlank()) {
            if (!session.getExpectedChecksum().equalsIgnoreCase(assembly.checksum())) {
                transferStorageService.deleteTransferDirectory(user.getId(), uploadId);
                session.setStatus(UploadSessionStatus.FAILED);
                uploadSessionRepository.save(session);
                throw new BadRequestException("Final file checksum mismatch. Expected: "
                        + session.getExpectedChecksum() + ", computed: " + assembly.checksum());
            }
        }

        String storedFilename = UUID.randomUUID() + "_" + session.getFilename();

        // Move to final storage location
        try {
            transferStorageService.moveAssemblyToFinalLocation(assembly.path(), user.getId(), storedFilename);
        } catch (Exception e) {
            session.setStatus(UploadSessionStatus.FAILED);
            uploadSessionRepository.save(session);
            throw e;
        }

        // Persist File metadata in database
        File savedFile = persistCompletedFile(session, storedFilename, user);

        // Clean up temporary chunks
        transferStorageService.deleteTransferDirectory(user.getId(), uploadId);

        return FileResponse.fromEntity(savedFile);
    }

    @Transactional
    protected File persistCompletedFile(UploadSession session, String storedFilename, User user) {
        Path path = fileStorageService.getFilePath(user.getId(), storedFilename);
        try {
            File file = new File(
                    session.getFilename(),
                    storedFilename,
                    path.toString(),
                    session.getFileSize(),
                    session.getContentType(),
                    user,
                    session.getFolder()
            );
            File savedFile = fileRepository.save(file);

            session.setStatus(UploadSessionStatus.COMPLETED);
            session.setActualChecksum(session.getActualChecksum());
            session.setFile(savedFile);
            uploadSessionRepository.save(session);

            return savedFile;
        } catch (Exception e) {
            fileStorageService.deleteFile(user.getId(), storedFilename);
            session.setStatus(UploadSessionStatus.FAILED);
            uploadSessionRepository.save(session);
            throw e;
        }
    }

    @Transactional
    public void cancelSession(String uploadId, User user) {
        UploadSession session = uploadSessionRepository.findByIdAndOwner_Id(uploadId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Transfer session not found: " + uploadId));

        if (session.getStatus() == UploadSessionStatus.COMPLETED) {
            throw new ConflictException("Cannot cancel an already completed upload session");
        }

        session.setStatus(UploadSessionStatus.CANCELLED);
        uploadSessionRepository.save(session);

        uploadChunkRepository.deleteBySession_Id(uploadId);
        transferStorageService.deleteTransferDirectory(user.getId(), uploadId);
    }

    public void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        List<UploadSession> expiredSessions = uploadSessionRepository.findByExpiresAtBeforeAndStatusNotIn(
                now,
                List.of(UploadSessionStatus.COMPLETED, UploadSessionStatus.CANCELLED, UploadSessionStatus.EXPIRED)
        );

        for (UploadSession session : expiredSessions) {
            try {
                session.setStatus(UploadSessionStatus.EXPIRED);
                uploadSessionRepository.save(session);
                uploadChunkRepository.deleteBySession_Id(session.getId());
                transferStorageService.deleteTransferDirectory(session.getOwner().getId(), session.getId());
                log.info("Cleaned up expired transfer session: {}", session.getId());
            } catch (Exception e) {
                log.warn("Failed to clean up expired session {}: {}", session.getId(), e.getMessage());
            }
        }

        // Clean up sessions stuck in FINALIZING for more than 30 minutes (potential backend crash during assembly)
        LocalDateTime stuckCutoff = now.minusMinutes(30);
        List<UploadSession> stuckSessions = uploadSessionRepository.findByStatusAndUpdatedAtBefore(
                UploadSessionStatus.FINALIZING, stuckCutoff
        );
        for (UploadSession session : stuckSessions) {
            try {
                session.setStatus(UploadSessionStatus.FAILED);
                uploadSessionRepository.save(session);
                transferStorageService.deleteTransferDirectory(session.getOwner().getId(), session.getId());
                log.warn("Marked stuck finalizing transfer session as FAILED: {}", session.getId());
            } catch (Exception e) {
                log.warn("Failed to clean up stuck session {}: {}", session.getId(), e.getMessage());
            }
        }
    }

    private UploadSession getActiveSession(String uploadId, Long userId) {
        UploadSession session = uploadSessionRepository.findByIdAndOwner_Id(uploadId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer session not found: " + uploadId));

        if (session.getStatus() == UploadSessionStatus.COMPLETED) {
            throw new ConflictException("Transfer session is already completed");
        }
        if (session.getStatus() == UploadSessionStatus.CANCELLED) {
            throw new ConflictException("Transfer session was cancelled");
        }
        if (session.getStatus() == UploadSessionStatus.EXPIRED || LocalDateTime.now().isAfter(session.getExpiresAt())) {
            session.setStatus(UploadSessionStatus.EXPIRED);
            uploadSessionRepository.save(session);
            throw new ConflictException("Transfer session has expired");
        }
        if (session.getStatus() == UploadSessionStatus.FAILED) {
            throw new ConflictException("Transfer session has failed");
        }

        return session;
    }

    private long calculateExpectedChunkSize(UploadSession session, int chunkNumber) {
        if (chunkNumber < session.getTotalChunks() - 1) {
            return session.getChunkSize();
        }
        return session.getFileSize() - ((long) (session.getTotalChunks() - 1) * session.getChunkSize());
    }
}
