package com.homecloud.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "upload_sessions", indexes = {
        @Index(name = "idx_upload_sessions_user_status", columnList = "user_id, status"),
        @Index(name = "idx_upload_sessions_expires_at", columnList = "expiresAt")
})
public class UploadSession {

    @Id
    @Column(nullable = false, length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    @JsonIgnore
    private Folder folder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    @JsonIgnore
    private File file;

    @Column(nullable = false)
    private String filename;

    private String contentType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private Long chunkSize;

    @Column(nullable = false)
    private Integer totalChunks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UploadSessionStatus status;

    @Column(length = 128)
    private String expectedChecksum;

    @Column(length = 128)
    private String actualChecksum;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<UploadChunk> chunks = new ArrayList<>();

    public UploadSession() {}

    public UploadSession(String id, User owner, Folder folder, String filename, String contentType,
                         Long fileSize, Long chunkSize, Integer totalChunks,
                         String expectedChecksum, LocalDateTime expiresAt) {
        this.id = id;
        this.owner = owner;
        this.folder = folder;
        this.filename = filename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.chunkSize = chunkSize;
        this.totalChunks = totalChunks;
        this.status = UploadSessionStatus.INITIATED;
        this.expectedChecksum = expectedChecksum;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = UploadSessionStatus.INITIATED;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }

    public Folder getFolder() { return folder; }
    public void setFolder(Folder folder) { this.folder = folder; }

    public File getFile() { return file; }
    public void setFile(File file) { this.file = file; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public Long getChunkSize() { return chunkSize; }
    public void setChunkSize(Long chunkSize) { this.chunkSize = chunkSize; }

    public Integer getTotalChunks() { return totalChunks; }
    public void setTotalChunks(Integer totalChunks) { this.totalChunks = totalChunks; }

    public UploadSessionStatus getStatus() { return status; }
    public void setStatus(UploadSessionStatus status) { this.status = status; }

    public String getExpectedChecksum() { return expectedChecksum; }
    public void setExpectedChecksum(String expectedChecksum) { this.expectedChecksum = expectedChecksum; }

    public String getActualChecksum() { return actualChecksum; }
    public void setActualChecksum(String actualChecksum) { this.actualChecksum = actualChecksum; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public List<UploadChunk> getChunks() { return chunks; }
    public void setChunks(List<UploadChunk> chunks) { this.chunks = chunks; }
}
