package com.homecloud.backend.dto;

public class StorageStatsResponse {

    private long usedBytes;
    private long fileCount;
    private long folderCount;

    public StorageStatsResponse() {}

    public StorageStatsResponse(long usedBytes, long fileCount, long folderCount) {
        this.usedBytes = usedBytes;
        this.fileCount = fileCount;
        this.folderCount = folderCount;
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public long getFileCount() {
        return fileCount;
    }

    public long getFolderCount() {
        return folderCount;
    }
}
