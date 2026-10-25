package com.homecloud.backend.service;

import com.homecloud.backend.entity.File;
import com.homecloud.backend.entity.Folder;
import com.homecloud.backend.entity.User;
import com.homecloud.backend.repository.FileRepository;
import com.homecloud.backend.repository.FolderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;

    public FolderService(FolderRepository folderRepository, FileRepository fileRepository) {
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
    }

    public Folder createFolder(String name, Long parentId, User user) {
        String cleanName = cleanName(name);
        Folder parent = null;

        if (parentId != null) {
            parent = getFolder(parentId, user.getId());
        }

        boolean exists = parent == null
                ? folderRepository.existsByNameAndOwner_IdAndParentIsNull(cleanName, user.getId())
                : folderRepository.existsByNameAndOwner_IdAndParent_Id(cleanName, user.getId(), parent.getId());

        if (exists) {
            throw new IllegalArgumentException("A folder with this name already exists");
        }

        return folderRepository.save(new Folder(cleanName, user, parent));
    }

    public List<Folder> listFolders(Long parentId, Long userId) {
        if (parentId == null) {
            return folderRepository.findByOwner_IdAndParentIsNullOrderByNameAsc(userId);
        }
        // Also verifies the parent belongs to this user.
        getFolder(parentId, userId);
        return folderRepository.findByOwner_IdAndParent_IdOrderByNameAsc(userId, parentId);
    }

    public Folder getFolder(Long folderId, Long userId) {
        return folderRepository.findByIdAndOwner_Id(folderId, userId)
                .orElseThrow(() -> new RuntimeException("Folder not found"));
    }

    public Folder renameFolder(Long folderId, String name, Long userId) {
        Folder folder = getFolder(folderId, userId);
        String cleanName = cleanName(name);
        Folder parent = folder.getParent();

        boolean exists = parent == null
                ? folderRepository.existsByNameAndOwner_IdAndParentIsNull(cleanName, userId)
                : folderRepository.existsByNameAndOwner_IdAndParent_Id(cleanName, userId, parent.getId());

        if (exists && !folder.getName().equals(cleanName)) {
            throw new IllegalArgumentException("A folder with this name already exists");
        }

        folder.setName(cleanName);
        return folderRepository.save(folder);
    }

    public void deleteFolder(Long folderId, Long userId) {
        Folder folder = getFolder(folderId, userId);

        List<Folder> children = listFolders(folderId, userId);
        if (!children.isEmpty()) {
            throw new IllegalArgumentException("Folder is not empty; delete or move its subfolders first");
        }

        List<File> files = fileRepository.findByOwner_IdAndFolder_Id(userId, folderId);
        if (!files.isEmpty()) {
            throw new IllegalArgumentException("Folder is not empty; delete or move its files first");
        }

        folderRepository.delete(folder);
    }

    private String cleanName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Folder name cannot be empty");
        }
        String clean = java.nio.file.Paths.get(name.trim()).getFileName().toString();
        if (clean.isBlank() || clean.equals(".") || clean.equals("..")) {
            throw new IllegalArgumentException("Invalid folder name");
        }
        return clean;
    }
}
