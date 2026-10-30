package com.homecloud.backend.service;

import com.homecloud.backend.dto.StorageStatsResponse;
import com.homecloud.backend.entity.File;
import com.homecloud.backend.entity.FileStatus;
import com.homecloud.backend.entity.Folder;
import com.homecloud.backend.entity.User;
import com.homecloud.backend.exception.BadRequestException;
import com.homecloud.backend.exception.ResourceNotFoundException;
import com.homecloud.backend.repository.FileRepository;
import com.homecloud.backend.repository.FolderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final FileStorageService fileStorageService;
    private final FolderService folderService;
    private final TransactionTemplate transactionTemplate;

    public FileService(FileRepository fileRepository,
                       FolderRepository folderRepository,
                       FileStorageService fileStorageService,
                       FolderService folderService) {
        this(fileRepository, folderRepository, fileStorageService, folderService, null);
    }

    @Autowired
    public FileService(FileRepository fileRepository,
                       FolderRepository folderRepository,
                       FileStorageService fileStorageService,
                       FolderService folderService,
                       @Autowired(required = false) PlatformTransactionManager transactionManager) {
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.fileStorageService = fileStorageService;
        this.folderService = folderService;
        this.transactionTemplate = transactionManager != null ? new TransactionTemplate(transactionManager) : null;
    }

    public File uploadFile(MultipartFile file, User user, Long folderId) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User must not be null");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BadRequestException("Invalid file name");
        }

        Folder folder = folderId == null ? null : folderService.getFolder(folderId, user.getId());

        String sanitizedFilename = fileStorageService.sanitizeFilename(originalFilename);
        String storedFilename = fileStorageService.storeFile(file, user.getId());
        Path path = fileStorageService.getFilePath(user.getId(), storedFilename);

        try {
            File entity = new File(
                    sanitizedFilename,
                    storedFilename,
                    path.toString(),
                    file.getSize(),
                    file.getContentType(),
                    user,
                    folder
            );
            return fileRepository.save(entity);
        } catch (Exception e) {
            fileStorageService.deleteFile(user.getId(), storedFilename);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<File> getUserFiles(Long userId, Long folderId) {
        if (folderId == null) {
            return fileRepository.findByOwner_IdAndFolderIsNullAndStatusOrderByFilenameAsc(userId, FileStatus.ACTIVE);
        }
        folderService.getFolder(folderId, userId);
        return fileRepository.findByOwner_IdAndFolder_IdAndStatusOrderByFilenameAsc(userId, folderId, FileStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public File getUserFile(Long fileId, Long userId) {
        return fileRepository.findByIdAndOwner_Id(fileId, userId)
                .filter(f -> f.getStatus() == FileStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
    }

    @Transactional(readOnly = true)
    public List<File> searchUserFiles(Long userId, String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        return fileRepository.findByOwner_IdAndFilenameContainingIgnoreCaseAndStatusOrderByFilenameAsc(
                userId, query.trim(), FileStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public StorageStatsResponse getStorageStats(Long userId) {
        long usedBytes = fileRepository.sumSizeByOwner_IdAndStatus(userId, FileStatus.ACTIVE);
        long fileCount = fileRepository.countByOwner_IdAndStatus(userId, FileStatus.ACTIVE);
        long folderCount = folderRepository.countByOwner_Id(userId);
        return new StorageStatsResponse(usedBytes, fileCount, folderCount);
    }

    @Transactional
    public File renameFile(Long fileId, Long userId, String newFilename) {
        String cleanName = fileStorageService.sanitizeFilename(newFilename);
        File file = getUserFile(fileId, userId);
        file.setFilename(cleanName);
        return fileRepository.save(file);
    }

    @Transactional
    public File moveFile(Long fileId, Long userId, Long folderId) {
        File file = getUserFile(fileId, userId);
        Folder folder = folderId == null ? null : folderService.getFolder(folderId, userId);
        file.setFolder(folder);
        return fileRepository.save(file);
    }

    /**
     * Reliable two-phase deletion workflow:
     * Phase 1: Safely record recoverable PENDING_DELETION state in database. If DB fails here,
     *          the physical data on disk is untouched and completely safe.
     * Phase 2: Physically delete the file from the storage layer. If filesystem fails here,
     *          metadata is preserved in PENDING_DELETION state, allowing safe retries without
     *          silently orphaning physical files or losing metadata.
     * Phase 3: Remove the database entity once physical data is deleted. If DB fails here,
     *          recovery job (purgePendingDeletions) re-verifies and safely purges the orphan DB entity.
     */
    public void deleteFile(Long fileId, Long userId) {
        // Phase 1: Validate ownership and record durable PENDING_DELETION state in database
        File file;
        if (transactionTemplate != null) {
            file = transactionTemplate.execute(status -> recordPendingDeletion(fileId, userId));
        } else {
            file = recordPendingDeletion(fileId, userId);
        }

        // Phase 2: Delete physical file from filesystem storage
        fileStorageService.deleteFile(userId, file.getStoredFilename());

        // Phase 3: Remove database record
        if (transactionTemplate != null) {
            transactionTemplate.executeWithoutResult(status -> removeFileRecord(file));
        } else {
            removeFileRecord(file);
        }
    }

    private File recordPendingDeletion(Long fileId, Long userId) {
        File file = fileRepository.findByIdAndOwner_Id(fileId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        if (file.getStatus() != FileStatus.PENDING_DELETION) {
            file.setStatus(FileStatus.PENDING_DELETION);
            fileRepository.save(file);
            fileRepository.flush();
        }
        return file;
    }

    private void removeFileRecord(File file) {
        fileRepository.delete(file);
        fileRepository.flush();
    }

    /**
     * Purges pending deletions that failed midway (e.g. due to server crash or temporary filesystem lock).
     * Retries physical file deletion and removes database metadata if successful.
     *
     * @return number of successfully purged files
     */
    @Transactional
    public int purgePendingDeletions() {
        List<File> pendingFiles = fileRepository.findByStatus(FileStatus.PENDING_DELETION);
        int purgedCount = 0;
        for (File file : pendingFiles) {
            try {
                fileStorageService.deleteFile(file.getOwner().getId(), file.getStoredFilename());
                fileRepository.delete(file);
                fileRepository.flush();
                purgedCount++;
            } catch (Exception e) {
                log.warn("Failed to purge pending deletion for file ID {}: {}", file.getId(), e.getMessage());
            }
        }
        return purgedCount;
    }
}
