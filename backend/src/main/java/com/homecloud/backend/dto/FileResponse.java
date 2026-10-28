package com.homecloud.backend.dto;

import com.homecloud.backend.entity.File;

import java.time.LocalDateTime;

public class FileResponse {

    private Long id;
    private String filename;
    private Long size;
    private String contentType;
    private Long folderId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public FileResponse() {}

    public FileResponse(Long id, String filename, Long size, String contentType,
                        Long folderId, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.filename = filename;
        this.size = size;
        this.contentType = contentType;
        this.folderId = folderId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static FileResponse fromEntity(File file) {
        if (file == null) return null;
        return new FileResponse(
                file.getId(),
                file.getFilename(),
                file.getSize(),
                file.getContentType(),
                file.getFolderId(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public Long getSize() {
        return size;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getFolderId() {
        return folderId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
