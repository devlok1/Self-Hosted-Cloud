package com.homecloud.backend.service;

import com.homecloud.backend.entity.File;
import com.homecloud.backend.entity.FileStatus;
import com.homecloud.backend.entity.Folder;
import com.homecloud.backend.entity.User;
import com.homecloud.backend.exception.BadRequestException;
import com.homecloud.backend.exception.ConflictException;
import com.homecloud.backend.exception.ResourceNotFoundException;
import com.homecloud.backend.exception.StorageException;
import com.homecloud.backend.repository.FileRepository;
import com.homecloud.backend.repository.FolderRepository;
import com.homecloud.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class FileRenameSynchronizationTest {

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FolderService folderService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @TempDir
    Path tempStorageDir;

    private FileStorageService fileStorageService;
    private FileService fileService;
    private User testUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        fileRepository.deleteAll();
        folderRepository.deleteAll();
        userRepository.deleteAll();

        fileStorageService = new FileStorageService(tempStorageDir.toString());
        fileService = new FileService(fileRepository, folderRepository, fileStorageService, folderService, transactionManager);

        testUser = userRepository.save(new User("owner@homecloud.test", "password123"));
        otherUser = userRepository.save(new User("other@homecloud.test", "password123"));
    }

    @Test
    @DisplayName("Successful file rename updates physical file, database metadata, and preserves content")
    void renameFile_successfulPhysicalAndDatabaseSync() throws IOException {
        String content = "Hello HomeCloud physical storage rename synchronization!";
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "original_doc.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8));

        File uploaded = fileService.uploadFile(multipart, testUser, null);
        Long fileId = uploaded.getId();
        String originalStoredFilename = uploaded.getStoredFilename();
        Path originalPath = fileStorageService.getFilePath(testUser.getId(), originalStoredFilename);

        assertTrue(Files.exists(originalPath), "Original physical file must exist on disk");

        // Execute rename
        File renamed = fileService.renameFile(fileId, testUser.getId(), "renamed_doc.txt");

        // 1. Verify entity metadata
        assertEquals("renamed_doc.txt", renamed.getFilename());
        assertNotEquals(originalStoredFilename, renamed.getStoredFilename());
        assertTrue(renamed.getStoredFilename().endsWith("_renamed_doc.txt"));

        // 2. Verify physical filesystem synchronization
        Path newPath = fileStorageService.getFilePath(testUser.getId(), renamed.getStoredFilename());
        assertFalse(Files.exists(originalPath), "Old physical file must no longer exist at original path");
        assertTrue(Files.exists(newPath), "New physical file must exist at renamed path");
        assertEquals(content, Files.readString(newPath, StandardCharsets.UTF_8), "File contents must remain unchanged");

        // 3. Verify database state
        File fromDb = fileRepository.findById(fileId).orElseThrow();
        assertEquals("renamed_doc.txt", fromDb.getFilename());
        assertEquals(renamed.getStoredFilename(), fromDb.getStoredFilename());
        assertEquals(newPath.toString(), fromDb.getStoragePath());
        assertEquals(FileStatus.ACTIVE, fromDb.getStatus());
    }

    @Test
    @DisplayName("Rename file handles Unicode characters and spaces in filenames")
    void renameFile_handlesUnicodeAndSpaces() throws IOException {
        String content = "Unicode file content";
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "test.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8));

        File uploaded = fileService.uploadFile(multipart, testUser, null);
        File renamed = fileService.renameFile(uploaded.getId(), testUser.getId(), "私の 写真 2026.png");

        assertEquals("私の 写真 2026.png", renamed.getFilename());
        assertTrue(renamed.getStoredFilename().endsWith("_私の 写真 2026.png"));

        Path physicalPath = fileStorageService.getFilePath(testUser.getId(), renamed.getStoredFilename());
        assertTrue(Files.exists(physicalPath));
        assertEquals(content, Files.readString(physicalPath, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Rename file to identical name is a safe no-op")
    void renameFile_identicalNameIsNoOp() {
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "same.txt", "text/plain", "Same content".getBytes(StandardCharsets.UTF_8));

        File uploaded = fileService.uploadFile(multipart, testUser, null);
        String storedFilename = uploaded.getStoredFilename();

        File renamed = fileService.renameFile(uploaded.getId(), testUser.getId(), "same.txt");

        assertEquals("same.txt", renamed.getFilename());
        assertEquals(storedFilename, renamed.getStoredFilename());
    }

    @Test
    @DisplayName("Rename file rejects duplicate filename in the same folder")
    void renameFile_rejectsDuplicateFilenameInSameFolder() {
        MockMultipartFile file1 = new MockMultipartFile(
                "file", "alpha.txt", "text/plain", "Alpha".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "beta.txt", "text/plain", "Beta".getBytes(StandardCharsets.UTF_8));

        fileService.uploadFile(file1, testUser, null);
        File uploaded2 = fileService.uploadFile(file2, testUser, null);

        assertThrows(ConflictException.class, () ->
                fileService.renameFile(uploaded2.getId(), testUser.getId(), "alpha.txt"));

        // Verify beta.txt remains untouched
        File fromDb = fileRepository.findById(uploaded2.getId()).orElseThrow();
        assertEquals("beta.txt", fromDb.getFilename());
    }

    @Test
    @DisplayName("Renaming file inside a nested folder works and respects folder boundaries")
    void renameFile_insideNestedFolder() throws IOException {
        Folder folder = folderService.createFolder("Documents", null, testUser);

        MockMultipartFile multipart = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "PDF Content".getBytes(StandardCharsets.UTF_8));

        File uploaded = fileService.uploadFile(multipart, testUser, folder.getId());
        assertEquals(folder.getId(), uploaded.getFolderId());

        File renamed = fileService.renameFile(uploaded.getId(), testUser.getId(), "annual_report_2026.pdf");

        assertEquals("annual_report_2026.pdf", renamed.getFilename());
        assertEquals(folder.getId(), renamed.getFolderId());

        Path path = fileStorageService.getFilePath(testUser.getId(), renamed.getStoredFilename());
        assertTrue(Files.exists(path));
    }

    @Test
    @DisplayName("Rename file rejects path traversal attempts and invalid names")
    void renameFile_rejectsInvalidNamesAndPathTraversal() {
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "valid.txt", "text/plain", "Valid".getBytes(StandardCharsets.UTF_8));
        File uploaded = fileService.uploadFile(multipart, testUser, null);

        assertThrows(BadRequestException.class, () ->
                fileService.renameFile(uploaded.getId(), testUser.getId(), ""));

        assertThrows(BadRequestException.class, () ->
                fileService.renameFile(uploaded.getId(), testUser.getId(), "   "));

        assertThrows(BadRequestException.class, () ->
                fileService.renameFile(uploaded.getId(), testUser.getId(), "../etc/passwd"));

        assertThrows(BadRequestException.class, () ->
                fileService.renameFile(uploaded.getId(), testUser.getId(), "sub/file.txt"));

        assertThrows(BadRequestException.class, () ->
                fileService.renameFile(uploaded.getId(), testUser.getId(), "..\\windows\\system32"));
    }

    @Test
    @DisplayName("Cross-user rename attempt is rejected with ResourceNotFoundException")
    void renameFile_crossUserRejected() {
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "userA_secret.txt", "text/plain", "Secret".getBytes(StandardCharsets.UTF_8));
        File uploaded = fileService.uploadFile(multipart, testUser, null);

        assertThrows(ResourceNotFoundException.class, () ->
                fileService.renameFile(uploaded.getId(), otherUser.getId(), "stolen.txt"));

        File fromDb = fileRepository.findById(uploaded.getId()).orElseThrow();
        assertEquals("userA_secret.txt", fromDb.getFilename());
    }

    @Test
    @DisplayName("Filesystem rename failure does not corrupt database state")
    void renameFile_filesystemFailureLeavesDatabaseUntouched() {
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "missing_physical.txt", "text/plain", "Content".getBytes(StandardCharsets.UTF_8));
        File uploaded = fileService.uploadFile(multipart, testUser, null);

        // Delete physical file behind the scenes to simulate filesystem failure
        Path physicalPath = fileStorageService.getFilePath(testUser.getId(), uploaded.getStoredFilename());
        try {
            Files.deleteIfExists(physicalPath);
        } catch (IOException ignored) {}

        assertThrows(StorageException.class, () ->
                fileService.renameFile(uploaded.getId(), testUser.getId(), "will_fail.txt"));

        File fromDb = fileRepository.findById(uploaded.getId()).orElseThrow();
        assertEquals("missing_physical.txt", fromDb.getFilename());
    }

    @Test
    @DisplayName("Database update failure compensates by reverting physical file rename")
    void renameFile_databaseFailureRevertsFilesystemRename() {
        MockMultipartFile multipart = new MockMultipartFile(
                "file", "doc_to_fail.txt", "text/plain", "Important doc".getBytes(StandardCharsets.UTF_8));
        File uploaded = fileService.uploadFile(multipart, testUser, null);
        String originalStoredFilename = uploaded.getStoredFilename();
        Path originalPath = fileStorageService.getFilePath(testUser.getId(), originalStoredFilename);

        // Create a FileRepository proxy that throws on save / saveAndFlush
        FileRepository failingRepo = (FileRepository) java.lang.reflect.Proxy.newProxyInstance(
                FileRepository.class.getClassLoader(),
                new Class<?>[]{FileRepository.class},
                (proxy, method, args) -> {
                    if ("saveAndFlush".equals(method.getName()) || "save".equals(method.getName())) {
                        throw new org.springframework.dao.DataIntegrityViolationException("Simulated DB failure");
                    }
                    return method.invoke(fileRepository, args);
                }
        );

        FileService serviceWithFailingRepo = new FileService(
                failingRepo, folderRepository, fileStorageService, folderService, transactionManager);

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                serviceWithFailingRepo.renameFile(uploaded.getId(), testUser.getId(), "should_revert.txt"));

        // Verify physical file was compensated: original file is restored on disk!
        assertTrue(Files.exists(originalPath), "Original physical file must be restored after compensation rollback");

        int underscoreIdx = originalStoredFilename.indexOf('_');
        String uuidPrefix = originalStoredFilename.substring(0, underscoreIdx);
        Path targetPath = fileStorageService.getUserDirectory(testUser.getId()).resolve(uuidPrefix + "_should_revert.txt");
        assertFalse(Files.exists(targetPath), "Renamed target physical file must no longer exist after compensation rollback");

        // Verify database metadata still has original filename
        File fromDb = fileRepository.findById(uploaded.getId()).orElseThrow();
        assertEquals("doc_to_fail.txt", fromDb.getFilename());
        assertEquals(originalStoredFilename, fromDb.getStoredFilename());
    }
}
