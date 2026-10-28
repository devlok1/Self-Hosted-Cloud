package com.homecloud.backend.service;

import com.homecloud.backend.dto.BreadcrumbItem;
import com.homecloud.backend.dto.FolderDetailResponse;
import com.homecloud.backend.entity.File;
import com.homecloud.backend.entity.Folder;
import com.homecloud.backend.entity.User;
import com.homecloud.backend.exception.BadRequestException;
import com.homecloud.backend.exception.ConflictException;
import com.homecloud.backend.exception.ResourceNotFoundException;
import com.homecloud.backend.repository.FileRepository;
import com.homecloud.backend.repository.FolderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class FolderService {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1F\\x7F]");

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;

    public FolderService(FolderRepository folderRepository, FileRepository fileRepository) {
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
    }

    @Transactional
    public Folder createFolder(String name, Long parentId, User user) {
        String cleanName = sanitizeFolderName(name);
        Folder parent = null;

        if (parentId != null) {
            parent = getFolder(parentId, user.getId());
        }

        boolean exists = parent == null
                ? folderRepository.existsByNameAndOwner_IdAndParentIsNull(cleanName, user.getId())
                : folderRepository.existsByNameAndOwner_IdAndParent_Id(cleanName, user.getId(), parent.getId());

        if (exists) {
            throw new ConflictException("A folder with this name already exists in this location");
        }

        return folderRepository.save(new Folder(cleanName, user, parent));
    }

    @Transactional(readOnly = true)
    public List<Folder> listFolders(Long parentId, Long userId) {
        if (parentId == null) {
            return folderRepository.findByOwner_IdAndParentIsNullOrderByNameAsc(userId);
        }
        // Verifies the parent exists and belongs to this user
        getFolder(parentId, userId);
        return folderRepository.findByOwner_IdAndParent_IdOrderByNameAsc(userId, parentId);
    }

    @Transactional(readOnly = true)
    public Folder getFolder(Long folderId, Long userId) {
        return folderRepository.findByIdAndOwner_Id(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found"));
    }

    @Transactional(readOnly = true)
    public FolderDetailResponse getFolderDetails(Long folderId, Long userId) {
        Folder folder = getFolder(folderId, userId);
        List<BreadcrumbItem> breadcrumbs = new ArrayList<>();

        Folder current = folder;
        Set<Long> visited = new HashSet<>();
        while (current != null && visited.add(current.getId())) {
            breadcrumbs.add(new BreadcrumbItem(current.getId(), current.getName()));
            current = current.getParent();
        }
        Collections.reverse(breadcrumbs);

        return new FolderDetailResponse(
                folder.getId(),
                folder.getName(),
                folder.getParentId(),
                breadcrumbs,
                folder.getCreatedAt(),
                folder.getUpdatedAt()
        );
    }

    @Transactional
    public Folder renameFolder(Long folderId, String name, Long userId) {
        Folder folder = getFolder(folderId, userId);
        String cleanName = sanitizeFolderName(name);

        if (folder.getName().equals(cleanName)) {
            return folder;
        }

        Folder parent = folder.getParent();
        boolean exists = parent == null
                ? folderRepository.existsByNameAndOwner_IdAndParentIsNull(cleanName, userId)
                : folderRepository.existsByNameAndOwner_IdAndParent_Id(cleanName, userId, parent.getId());

        if (exists) {
            throw new ConflictException("A folder with this name already exists in this location");
        }

        folder.setName(cleanName);
        return folderRepository.save(folder);
    }

    @Transactional
    public void deleteFolder(Long folderId, Long userId) {
        Folder folder = getFolder(folderId, userId);

        List<Folder> children = folderRepository.findByOwner_IdAndParent_IdOrderByNameAsc(userId, folderId);
        if (!children.isEmpty()) {
            throw new BadRequestException("Folder is not empty; delete or move its subfolders first");
        }

        List<File> files = fileRepository.findByOwner_IdAndFolder_Id(userId, folderId);
        if (!files.isEmpty()) {
            throw new BadRequestException("Folder is not empty; delete or move its files first");
        }

        folderRepository.delete(folder);
    }

    public String sanitizeFolderName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Folder name cannot be empty");
        }

        String base = Paths.get(name.trim()).getFileName().toString();
        String sanitized = CONTROL_CHARS.matcher(base).replaceAll("").trim();

        if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")) {
            throw new BadRequestException("Invalid folder name");
        }

        if (sanitized.length() > 255) {
            throw new BadRequestException("Folder name exceeds maximum allowed length of 255 characters");
        }

        return sanitized;
    }
}
