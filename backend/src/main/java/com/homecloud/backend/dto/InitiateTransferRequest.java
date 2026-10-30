package com.homecloud.backend.dto;

public class InitiateTransferRequest {

    private String filename;
    private Long fileSize;
    private Long chunkSize;
    private Long folderId;
    private String contentType;
    private String expectedChecksum;

    public InitiateTransferRequest() {}

    public InitiateTransferRequest(String filename, Long fileSize, Long chunkSize, Long folderId, String contentType, String expectedChecksum) {
        this.filename = filename;
        this.fileSize = fileSize;
        this.chunkSize = chunkSize;
        this.folderId = folderId;
        this.contentType = contentType;
        this.expectedChecksum = expectedChecksum;
    }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public Long getChunkSize() { return chunkSize; }
    public void setChunkSize(Long chunkSize) { this.chunkSize = chunkSize; }

    public Long getFolderId() { return folderId; }
    public void setFolderId(Long folderId) { this.folderId = folderId; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getExpectedChecksum() { return expectedChecksum; }
    public void setExpectedChecksum(String expectedChecksum) { this.expectedChecksum = expectedChecksum; }
}
