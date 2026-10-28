package com.homecloud.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public class FolderDetailResponse {

    private Long id;
    private String name;
    private Long parentId;
    private List<BreadcrumbItem> breadcrumbs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public FolderDetailResponse() {}

    public FolderDetailResponse(Long id, String name, Long parentId,
                                List<BreadcrumbItem> breadcrumbs,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.breadcrumbs = breadcrumbs;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public List<BreadcrumbItem> getBreadcrumbs() {
        return breadcrumbs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
