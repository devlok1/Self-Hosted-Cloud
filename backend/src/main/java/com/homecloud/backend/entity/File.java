package com.homecloud.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "files")
public class File {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false, unique = true)
    private String storedFilename;

    @Column(nullable = false, length = 1000)
    private String storagePath;

    @Column(nullable = false)
    private Long size;

    private String contentType;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    @JsonIgnore
    private Folder folder;

    public File() {}

    public File(String filename, String storedFilename, String storagePath,
                Long size, String contentType, User owner) {
        this(filename, storedFilename, storagePath, size, contentType, owner, null);
    }

    public File(String filename, String storedFilename, String storagePath,
                Long size, String contentType, User owner, Folder folder) {
        this.filename = filename;
        this.storedFilename = storedFilename;
        this.storagePath = storagePath;
        this.size = size;
        this.contentType = contentType;
        this.owner = owner;
        this.folder = folder;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getStoredFilename() { return storedFilename; }
    public String getStoragePath() { return storagePath; }
    public Long getSize() { return size; }
    public String getContentType() { return contentType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public User getOwner() { return owner; }
    @JsonIgnore
    public Folder getFolder() { return folder; }

    @JsonProperty("folderId")
    public Long getFolderId() { return folder == null ? null : folder.getId(); }
    public void setFolder(Folder folder) { this.folder = folder; }
}
