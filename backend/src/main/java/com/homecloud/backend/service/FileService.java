package com.homecloud.backend.service;

import com.homecloud.backend.entity.File;
import com.homecloud.backend.entity.Folder;
import com.homecloud.backend.entity.User;
import com.homecloud.backend.repository.FileRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class FileService {

    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;
    private final FolderService folderService;

    public FileService(FileRepository fileRepository,
                       FileStorageService fileStorageService,
                       FolderService folderService) {
        this.fileRepository = fileRepository;
        this.fileStorageService = fileStorageService;
        this.folderService = folderService;
    }

    public File uploadFile(MultipartFile file, User user, Long folderId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Invalid file name");
        }

        Folder folder = folderId == null ? null : folderService.getFolder(folderId, user.getId());
        String storedFilename = fileStorageService.storeFile(file);
        Path path = fileStorageService.getFilePath(storedFilename);

        try {
            File entity = new File(
                    java.nio.file.Paths.get(originalFilename).getFileName().toString(),
                    storedFilename,
                    path.toString(),
                    file.getSize(),
                    file.getContentType(),
                    user,
                    folder
            );
            return fileRepository.save(entity);
        } catch (RuntimeException e) {
            Files.deleteIfExists(path);
            throw e;
        }
    }

    public List<File> getUserFiles(Long userId, Long folderId) {
        if (folderId == null) {
            return fileRepository.findByOwner_IdAndFolderIsNullOrderByFilenameAsc(userId);
        }
        folderService.getFolder(folderId, userId);
        return fileRepository.findByOwner_IdAndFolder_IdOrderByFilenameAsc(userId, folderId);
    }

    public File getUserFile(Long fileId, Long userId) {
        return fileRepository.findByIdAndOwner_Id(fileId, userId)
                .orElseThrow(() -> new RuntimeException("File not found"));
    }

    public File renameFile(Long fileId, Long userId, String newFilename) {
        if (newFilename == null || newFilename.isBlank()) {
            throw new IllegalArgumentException("File name cannot be empty");
        }

        String cleanName = java.nio.file.Paths.get(newFilename.trim()).getFileName().toString();
        if (cleanName.isBlank() || cleanName.equals(".") || cleanName.equals("..")) {
            throw new IllegalArgumentException("Invalid file name");
        }

        File file = getUserFile(fileId, userId);
        file.setFilename(cleanName);
        return fileRepository.save(file);
    }

    public File moveFile(Long fileId, Long userId, Long folderId) {
        File file = getUserFile(fileId, userId);
        Folder folder = folderId == null ? null : folderService.getFolder(folderId, userId);
        file.setFolder(folder);
        return fileRepository.save(file);
    }

    public void deleteFile(Long fileId, Long userId) throws IOException {
        File file = getUserFile(fileId, userId);
        fileStorageService.deleteFile(file.getStoredFilename());
        fileRepository.delete(file);
    }
}
