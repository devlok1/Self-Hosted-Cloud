package com.homecloud.backend.dto;

import com.homecloud.backend.entity.UploadSessionStatus;

public class TransferChunkResponse {

    private String uploadId;
    private Integer chunkNumber;
    private Long size;
    private String checksum;
    private Integer uploadedChunksCount;
    private Integer totalChunks;
    private UploadSessionStatus status;

    public TransferChunkResponse() {}

    public TransferChunkResponse(String uploadId, Integer chunkNumber, Long size, String checksum,
                                 Integer uploadedChunksCount, Integer totalChunks, UploadSessionStatus status) {
        this.uploadId = uploadId;
        this.chunkNumber = chunkNumber;
        this.size = size;
        this.checksum = checksum;
        this.uploadedChunksCount = uploadedChunksCount;
        this.totalChunks = totalChunks;
        this.status = status;
    }

    public String getUploadId() { return uploadId; }
    public Integer getChunkNumber() { return chunkNumber; }
    public Long getSize() { return size; }
    public String getChecksum() { return checksum; }
    public Integer getUploadedChunksCount() { return uploadedChunksCount; }
    public Integer getTotalChunks() { return totalChunks; }
    public UploadSessionStatus getStatus() { return status; }
}
