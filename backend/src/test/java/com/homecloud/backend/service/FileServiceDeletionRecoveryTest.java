package com.homecloud.backend.service;

import com.homecloud.backend.entity.File;
import com.homecloud.backend.entity.FileStatus;
import com.homecloud.backend.entity.User;
import com.homecloud.backend.exception.ResourceNotFoundException;
import com.homecloud.backend.exception.StorageException;
import com.homecloud.backend.repository.FileRepository;
import com.homecloud.backend.repository.FolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceDeletionRecoveryTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private FolderService folderService;

    private FileService fileService;

    private User owner;
    private File activeFile;

    @BeforeEach
    void setUp() {
        fileService = new FileService(fileRepository, folderRepository, fileStorageService, folderService);

        owner = new User("alice@example.com", "pass");
        ReflectionTestUtils.setField(owner, "id", 1L);

        activeFile = new File("important_document.pdf", "uuid_stored_doc.pdf", "/storage/path", 1024L, "application/pdf", owner);
        ReflectionTestUtils.setField(activeFile, "id", 100L);
    }

    @Test
    @DisplayName("DB failure during deletion state transition: physical file is untouched and safe")
    void deleteFile_databaseFailureDuringStateTransition_physicalFileUntouched() {
        when(fileRepository.findByIdAndOwner_Id(100L, 1L)).thenReturn(Optional.of(activeFile));
        doThrow(new RuntimeException("Simulated database connection loss"))
                .when(fileRepository).save(any(File.class));

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                fileService.deleteFile(100L, 1L));

        assertEquals("Simulated database connection loss", thrown.getMessage());

        // Verify physical storage deletion was NEVER attempted
        verify(fileStorageService, never()).deleteFile(anyLong(), anyString());
        // Verify database deletion was NEVER attempted
        verify(fileRepository, never()).delete(any(File.class));
    }

    @Test
    @DisplayName("Filesystem failure during physical deletion: preserves metadata in PENDING_DELETION state")
    void deleteFile_filesystemFailure_preservesMetadataInPendingDeletionState() {
        when(fileRepository.findByIdAndOwner_Id(100L, 1L)).thenReturn(Optional.of(activeFile));
        doThrow(new StorageException("Simulated disk I/O lock"))
                .when(fileStorageService).deleteFile(1L, "uuid_stored_doc.pdf");

        StorageException thrown = assertThrows(StorageException.class, () ->
                fileService.deleteFile(100L, 1L));

        assertEquals("Simulated disk I/O lock", thrown.getMessage());

        // Verify entity transitioned to PENDING_DELETION and was persisted
        assertEquals(FileStatus.PENDING_DELETION, activeFile.getStatus());
        verify(fileRepository).save(activeFile);
        // Verify database entity was NOT prematurely removed so metadata is preserved
        verify(fileRepository, never()).delete(any(File.class));
    }

    @Test
    @DisplayName("Retry deletion after filesystem failure: successfully completes deletion")
    void deleteFile_retryAfterFilesystemFailure_successfullyCompletes() {
        // File is already in PENDING_DELETION state from previous failed attempt
        activeFile.setStatus(FileStatus.PENDING_DELETION);
        when(fileRepository.findByIdAndOwner_Id(100L, 1L)).thenReturn(Optional.of(activeFile));

        fileService.deleteFile(100L, 1L);

        // Physical deletion executed
        verify(fileStorageService).deleteFile(1L, "uuid_stored_doc.pdf");
        // Database record cleanly deleted
        verify(fileRepository).delete(activeFile);
    }

    @Test
    @DisplayName("Recovery purge: cleans up pending deletion files and removes DB records")
    void purgePendingDeletions_recoversAndPurgesPendingDeletions() {
        File file1 = new File("doc1.txt", "uuid1.txt", "path1", 50L, "text/plain", owner);
        ReflectionTestUtils.setField(file1, "id", 101L);
        file1.setStatus(FileStatus.PENDING_DELETION);

        File file2 = new File("doc2.txt", "uuid2.txt", "path2", 80L, "text/plain", owner);
        ReflectionTestUtils.setField(file2, "id", 102L);
        file2.setStatus(FileStatus.PENDING_DELETION);

        when(fileRepository.findByStatus(FileStatus.PENDING_DELETION))
                .thenReturn(List.of(file1, file2));

        int purged = fileService.purgePendingDeletions();

        assertEquals(2, purged);
        verify(fileStorageService).deleteFile(1L, "uuid1.txt");
        verify(fileStorageService).deleteFile(1L, "uuid2.txt");
        verify(fileRepository).delete(file1);
        verify(fileRepository).delete(file2);
    }

    @Test
    @DisplayName("User ownership authorization check: unauthorized user cannot delete file")
    void deleteFile_unauthorizedUser_throwsResourceNotFound() {
        Long unauthorizedUserId = 999L;
        when(fileRepository.findByIdAndOwner_Id(100L, unauthorizedUserId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                fileService.deleteFile(100L, unauthorizedUserId));

        verify(fileStorageService, never()).deleteFile(anyLong(), anyString());
        verify(fileRepository, never()).delete(any(File.class));
    }
}
