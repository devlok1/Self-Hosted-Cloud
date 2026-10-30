package com.homecloud.backend.dto;

import com.homecloud.backend.entity.UploadSession;
import com.homecloud.backend.entity.UploadSessionStatus;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class TransferSessionResponse {

    private String uploadId;
    private String filename;
    private Long fileSize;
    private Long chunkSize;
    private Integer totalChunks;
    private UploadSessionStatus status;
    private Long folderId;
    private List<Integer> uploadedChunks;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    public TransferSessionResponse() {}

    public TransferSessionResponse(String uploadId, String filename, Long fileSize, Long chunkSize,
                                   Integer totalChunks, UploadSessionStatus status, Long folderId,
                                   List<Integer> uploadedChunks, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.uploadId = uploadId;
        this.filename = filename;
        this.fileSize = fileSize;
        this.chunkSize = chunkSize;
        this.totalChunks = totalChunks;
        this.status = status;
        this.folderId = folderId;
        this.uploadedChunks = uploadedChunks != null ? uploadedChunks : Collections.emptyList();
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public static TransferSessionResponse fromEntity(UploadSession session, List<Integer> uploadedChunks) {
        Long folderId = session.getFolder() != null ? session.getFolder().getId() : null;
        return new TransferSessionResponse(
                session.getId(),
                session.getFilename(),
                session.getFileSize(),
                session.getChunkSize(),
                session.getTotalChunks(),
                session.getStatus(),
                folderId,
                uploadedChunks,
                session.getCreatedAt(),
                session.getExpiresAt()
        );
    }

    public String getUploadId() { return uploadId; }
    public String getFilename() { return filename; }
    public Long getFileSize() { return fileSize; }
    public Long getChunkSize() { return chunkSize; }
    public Integer getTotalChunks() { return totalChunks; }
    public UploadSessionStatus getStatus() { return status; }
    public Long getFolderId() { return folderId; }
    public List<Integer> getUploadedChunks() { return uploadedChunks; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
