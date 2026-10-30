package com.homecloud.backend.dto;

import com.homecloud.backend.entity.UploadSession;
import com.homecloud.backend.entity.UploadSessionStatus;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class TransferStatusResponse {

    private String uploadId;
    private String filename;
    private Long fileSize;
    private Long chunkSize;
    private Integer totalChunks;
    private UploadSessionStatus status;
    private List<Integer> uploadedChunks;
    private Integer uploadedChunksCount;
    private LocalDateTime expiresAt;
    private FileResponse completedFile;

    public TransferStatusResponse() {}

    public TransferStatusResponse(String uploadId, String filename, Long fileSize, Long chunkSize,
                                  Integer totalChunks, UploadSessionStatus status, List<Integer> uploadedChunks,
                                  LocalDateTime expiresAt, FileResponse completedFile) {
        this.uploadId = uploadId;
        this.filename = filename;
        this.fileSize = fileSize;
        this.chunkSize = chunkSize;
        this.totalChunks = totalChunks;
        this.status = status;
        this.uploadedChunks = uploadedChunks != null ? uploadedChunks : Collections.emptyList();
        this.uploadedChunksCount = this.uploadedChunks.size();
        this.expiresAt = expiresAt;
        this.completedFile = completedFile;
    }

    public static TransferStatusResponse fromEntity(UploadSession session, List<Integer> uploadedChunks) {
        FileResponse fileResp = session.getFile() != null ? FileResponse.fromEntity(session.getFile()) : null;
        return new TransferStatusResponse(
                session.getId(),
                session.getFilename(),
                session.getFileSize(),
                session.getChunkSize(),
                session.getTotalChunks(),
                session.getStatus(),
                uploadedChunks,
                session.getExpiresAt(),
                fileResp
        );
    }

    public String getUploadId() { return uploadId; }
    public String getFilename() { return filename; }
    public Long getFileSize() { return fileSize; }
    public Long getChunkSize() { return chunkSize; }
    public Integer getTotalChunks() { return totalChunks; }
    public UploadSessionStatus getStatus() { return status; }
    public List<Integer> getUploadedChunks() { return uploadedChunks; }
    public Integer getUploadedChunksCount() { return uploadedChunksCount; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public FileResponse getCompletedFile() { return completedFile; }
}
