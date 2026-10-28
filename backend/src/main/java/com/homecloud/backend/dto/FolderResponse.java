package com.homecloud.backend.dto;

import com.homecloud.backend.entity.Folder;

import java.time.LocalDateTime;

public class FolderResponse {

    private Long id;
    private String name;
    private Long parentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public FolderResponse() {}

    public FolderResponse(Long id, String name, Long parentId,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static FolderResponse fromEntity(Folder folder) {
        if (folder == null) return null;
        return new FolderResponse(
                folder.getId(),
                folder.getName(),
                folder.getParentId(),
                folder.getCreatedAt(),
                folder.getUpdatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getParentId() {
        return parentId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
