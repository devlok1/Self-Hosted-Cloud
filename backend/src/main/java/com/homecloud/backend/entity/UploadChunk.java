package com.homecloud.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "upload_chunks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_upload_chunks_session_chunk", columnNames = {"session_id", "chunk_number"})
}, indexes = {
        @Index(name = "idx_upload_chunks_session_id", columnList = "session_id")
})
public class UploadChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    @JsonIgnore
    private UploadSession session;

    @Column(name = "chunk_number", nullable = false)
    private Integer chunkNumber;

    @Column(nullable = false)
    private Long size;

    @Column(nullable = false, length = 128)
    private String checksum;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    public UploadChunk() {}

    public UploadChunk(UploadSession session, Integer chunkNumber, Long size, String checksum) {
        this.session = session;
        this.chunkNumber = chunkNumber;
        this.size = size;
        this.checksum = checksum;
        this.uploadedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public UploadSession getSession() { return session; }
    public void setSession(UploadSession session) { this.session = session; }

    public Integer getChunkNumber() { return chunkNumber; }
    public void setChunkNumber(Integer chunkNumber) { this.chunkNumber = chunkNumber; }

    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
