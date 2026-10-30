package com.homecloud.backend.entity;

public enum UploadSessionStatus {
    INITIATED,
    UPLOADING,
    FINALIZING,
    COMPLETED,
    FAILED,
    CANCELLED,
    EXPIRED
}
