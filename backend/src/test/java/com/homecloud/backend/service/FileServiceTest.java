package com.homecloud.backend.service;

import com.homecloud.backend.dto.StorageStatsResponse;
import com.homecloud.backend.entity.File;
import com.homecloud.backend.entity.Folder;
import com.homecloud.backend.entity.User;
import com.homecloud.backend.exception.BadRequestException;
import com.homecloud.backend.exception.ResourceNotFoundException;
import com.homecloud.backend.repository.FileRepository;
import com.homecloud.backend.repository.FolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private FolderService folderService;

    private FileService fileService;

    private User user;

    @BeforeEach
    void setUp() {
        fileService = new FileService(fileRepository, folderRepository, fileStorageService, folderService);
        user = new User("user@example.com", "pass");
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    @DisplayName("Upload file to root stores file and returns File entity")
    void uploadFile_rootSuccess() {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "hello world".getBytes()
        );

        when(fileStorageService.sanitizeFilename("notes.txt")).thenReturn("notes.txt");
        when(fileStorageService.storeFile(multipartFile, 1L)).thenReturn("uuid_notes.txt");
        when(fileStorageService.getFilePath(1L, "uuid_notes.txt")).thenReturn(Path.of("storage/uuid_notes.txt"));
        when(fileRepository.save(any(File.class))).thenAnswer(i -> i.getArgument(0));

        File saved = fileService.uploadFile(multipartFile, user, null);
        assertNotNull(saved);
        assertEquals("notes.txt", saved.getFilename());
        assertEquals("uuid_notes.txt", saved.getStoredFilename());
        assertEquals(11L, saved.getSize());
    }

    @Test
    @DisplayName("Upload rollback: deletes disk file if DB save throws an exception")
    void uploadFile_dbFailure_deletesStoredFile() {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "hello world".getBytes()
        );

        when(fileStorageService.sanitizeFilename("notes.txt")).thenReturn("notes.txt");
        when(fileStorageService.storeFile(multipartFile, 1L)).thenReturn("uuid_notes.txt");
        when(fileStorageService.getFilePath(1L, "uuid_notes.txt")).thenReturn(Path.of("storage/uuid_notes.txt"));
        when(fileRepository.save(any(File.class))).thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () ->
                fileService.uploadFile(multipartFile, user, null));

        verify(fileStorageService).deleteFile(1L, "uuid_notes.txt");
    }

    @Test
    @DisplayName("Rename file updates filename")
    void renameFile_success() {
        File file = new File("old.txt", "uuid", "path", 100L, "text/plain", user);
        ReflectionTestUtils.setField(file, "id", 2L);

        when(fileStorageService.sanitizeFilename("new.txt")).thenReturn("new.txt");
        when(fileRepository.findByIdAndOwner_Id(2L, 1L)).thenReturn(Optional.of(file));
        when(fileRepository.save(any(File.class))).thenAnswer(i -> i.getArgument(0));

        File renamed = fileService.renameFile(2L, 1L, "new.txt");
        assertEquals("new.txt", renamed.getFilename());
    }

    @Test
    @DisplayName("Move file to folder updates file folder reference")
    void moveFile_success() {
        File file = new File("file.txt", "uuid", "path", 100L, "text/plain", user);
        ReflectionTestUtils.setField(file, "id", 2L);

        Folder targetFolder = new Folder("Target", user, null);
        ReflectionTestUtils.setField(targetFolder, "id", 10L);

        when(fileRepository.findByIdAndOwner_Id(2L, 1L)).thenReturn(Optional.of(file));
        when(folderService.getFolder(10L, 1L)).thenReturn(targetFolder);
        when(fileRepository.save(any(File.class))).thenAnswer(i -> i.getArgument(0));

        File moved = fileService.moveFile(2L, 1L, 10L);
        assertNotNull(moved.getFolder());
        assertEquals(10L, moved.getFolderId());
    }

    @Test
    @DisplayName("Delete file deletes physical file and DB record")
    void deleteFile_success() {
        File file = new File("file.txt", "uuid_stored", "path", 100L, "text/plain", user);
        ReflectionTestUtils.setField(file, "id", 2L);

        when(fileRepository.findByIdAndOwner_Id(2L, 1L)).thenReturn(Optional.of(file));

        fileService.deleteFile(2L, 1L);

        verify(fileRepository).save(file);
        verify(fileStorageService).deleteFile(1L, "uuid_stored");
        verify(fileRepository).delete(file);
        assertEquals(com.homecloud.backend.entity.FileStatus.PENDING_DELETION, file.getStatus());
    }

    @Test
    @DisplayName("Search user files by query")
    void searchUserFiles() {
        when(fileRepository.findByOwner_IdAndFilenameContainingIgnoreCaseAndStatusOrderByFilenameAsc(1L, "report", com.homecloud.backend.entity.FileStatus.ACTIVE))
                .thenReturn(List.of(new File("report2026.pdf", "u", "p", 50L, "application/pdf", user)));

        List<File> results = fileService.searchUserFiles(1L, "report");
        assertEquals(1, results.size());
        assertEquals("report2026.pdf", results.get(0).getFilename());
    }

    @Test
    @DisplayName("Get storage stats computes bytes and counts")
    void getStorageStats() {
        when(fileRepository.sumSizeByOwner_IdAndStatus(1L, com.homecloud.backend.entity.FileStatus.ACTIVE)).thenReturn(5242880L);
        when(fileRepository.countByOwner_IdAndStatus(1L, com.homecloud.backend.entity.FileStatus.ACTIVE)).thenReturn(15L);
        when(folderRepository.countByOwner_Id(1L)).thenReturn(4L);

        StorageStatsResponse stats = fileService.getStorageStats(1L);
        assertEquals(5242880L, stats.getUsedBytes());
        assertEquals(15L, stats.getFileCount());
        assertEquals(4L, stats.getFolderCount());
    }

    @Test
    @DisplayName("Accessing another user's file throws ResourceNotFoundException")
    void getUserFile_otherUser_throwsNotFound() {
        when(fileRepository.findByIdAndOwner_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                fileService.getUserFile(99L, 1L));
    }
}
