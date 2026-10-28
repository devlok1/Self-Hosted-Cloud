package com.homecloud.backend.service;

import com.homecloud.backend.dto.StorageStatsResponse;
import com.homecloud.backend.entity.File;
import com.homecloud.backend.entity.Folder;
import com.homecloud.backend.entity.User;
import com.homecloud.backend.exception.BadRequestException;
import com.homecloud.backend.exception.ResourceNotFoundException;
import com.homecloud.backend.repository.FileRepository;
import com.homecloud.backend.repository.FolderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@Service
public class FileService {

    private final FileRepository fileRepository;
    private final FolderRepository folderRepository;
    private final FileStorageService fileStorageService;
    private final FolderService folderService;

    public FileService(FileRepository fileRepository,
                       FolderRepository folderRepository,
                       FileStorageService fileStorageService,
                       FolderService folderService) {
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.fileStorageService = fileStorageService;
        this.folderService = folderService;
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
            return fileRepository.findByOwner_IdAndFolderIsNullOrderByFilenameAsc(userId);
        }
        folderService.getFolder(folderId, userId);
        return fileRepository.findByOwner_IdAndFolder_IdOrderByFilenameAsc(userId, folderId);
    }

    @Transactional(readOnly = true)
    public File getUserFile(Long fileId, Long userId) {
        return fileRepository.findByIdAndOwner_Id(fileId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
    }

    @Transactional(readOnly = true)
    public List<File> searchUserFiles(Long userId, String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        return fileRepository.findByOwner_IdAndFilenameContainingIgnoreCaseOrderByFilenameAsc(userId, query.trim());
    }

    @Transactional(readOnly = true)
    public StorageStatsResponse getStorageStats(Long userId) {
        long usedBytes = fileRepository.sumSizeByOwner_Id(userId);
        long fileCount = fileRepository.countByOwner_Id(userId);
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

    @Transactional
    public void deleteFile(Long fileId, Long userId) {
        File file = getUserFile(fileId, userId);
        fileStorageService.deleteFile(userId, file.getStoredFilename());
        fileRepository.delete(file);
    }
}
