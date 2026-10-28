package com.homecloud.backend.service;

import com.homecloud.backend.dto.FolderDetailResponse;
import com.homecloud.backend.entity.File;
import com.homecloud.backend.entity.Folder;
import com.homecloud.backend.entity.User;
import com.homecloud.backend.exception.BadRequestException;
import com.homecloud.backend.exception.ConflictException;
import com.homecloud.backend.exception.ResourceNotFoundException;
import com.homecloud.backend.repository.FileRepository;
import com.homecloud.backend.repository.FolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FolderServiceTest {

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private FileRepository fileRepository;

    private FolderService folderService;

    private User user;

    @BeforeEach
    void setUp() {
        folderService = new FolderService(folderRepository, fileRepository);
        user = new User("user@example.com", "pass");
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    @DisplayName("Create root folder succeeds")
    void createFolder_rootSuccess() {
        when(folderRepository.existsByNameAndOwner_IdAndParentIsNull("Documents", 1L)).thenReturn(false);
        when(folderRepository.save(any(Folder.class))).thenAnswer(i -> i.getArgument(0));

        Folder folder = folderService.createFolder("Documents", null, user);
        assertNotNull(folder);
        assertEquals("Documents", folder.getName());
        assertNull(folder.getParent());
    }

    @Test
    @DisplayName("Creating folder with duplicate name in same parent throws ConflictException")
    void createFolder_duplicateName_throwsConflict() {
        when(folderRepository.existsByNameAndOwner_IdAndParentIsNull("Photos", 1L)).thenReturn(true);

        assertThrows(ConflictException.class, () ->
                folderService.createFolder("Photos", null, user));
    }

    @Test
    @DisplayName("Create child folder checks parent ownership")
    void createFolder_childFolder() {
        Folder parent = new Folder("Parent", user, null);
        ReflectionTestUtils.setField(parent, "id", 10L);

        when(folderRepository.findByIdAndOwner_Id(10L, 1L)).thenReturn(Optional.of(parent));
        when(folderRepository.existsByNameAndOwner_IdAndParent_Id("Child", 1L, 10L)).thenReturn(false);
        when(folderRepository.save(any(Folder.class))).thenAnswer(i -> i.getArgument(0));

        Folder child = folderService.createFolder("Child", 10L, user);
        assertNotNull(child);
        assertEquals("Child", child.getName());
        assertEquals(10L, child.getParentId());
    }

    @Test
    @DisplayName("Breadcrumbs resolution returns hierarchical path")
    void getFolderDetails_breadcrumbs() {
        Folder root = new Folder("Root", user, null);
        ReflectionTestUtils.setField(root, "id", 1L);

        Folder sub = new Folder("Sub", user, root);
        ReflectionTestUtils.setField(sub, "id", 2L);

        Folder subSub = new Folder("SubSub", user, sub);
        ReflectionTestUtils.setField(subSub, "id", 3L);

        when(folderRepository.findByIdAndOwner_Id(3L, 1L)).thenReturn(Optional.of(subSub));

        FolderDetailResponse details = folderService.getFolderDetails(3L, 1L);
        assertNotNull(details);
        assertEquals(3, details.getBreadcrumbs().size());
        assertEquals("Root", details.getBreadcrumbs().get(0).getName());
        assertEquals("Sub", details.getBreadcrumbs().get(1).getName());
        assertEquals("SubSub", details.getBreadcrumbs().get(2).getName());
    }

    @Test
    @DisplayName("Delete folder containing subfolders throws BadRequestException")
    void deleteFolder_nonEmptyWithSubfolders_throwsBadRequest() {
        Folder folder = new Folder("NonEmpty", user, null);
        ReflectionTestUtils.setField(folder, "id", 5L);

        when(folderRepository.findByIdAndOwner_Id(5L, 1L)).thenReturn(Optional.of(folder));
        when(folderRepository.findByOwner_IdAndParent_IdOrderByNameAsc(1L, 5L))
                .thenReturn(List.of(new Folder("Child", user, folder)));

        assertThrows(BadRequestException.class, () ->
                folderService.deleteFolder(5L, 1L));
    }

    @Test
    @DisplayName("Delete folder containing files throws BadRequestException")
    void deleteFolder_nonEmptyWithFiles_throwsBadRequest() {
        Folder folder = new Folder("NonEmpty", user, null);
        ReflectionTestUtils.setField(folder, "id", 5L);

        when(folderRepository.findByIdAndOwner_Id(5L, 1L)).thenReturn(Optional.of(folder));
        when(folderRepository.findByOwner_IdAndParent_IdOrderByNameAsc(1L, 5L)).thenReturn(Collections.emptyList());
        when(fileRepository.findByOwner_IdAndFolder_Id(1L, 5L)).thenReturn(List.of(new File()));

        assertThrows(BadRequestException.class, () ->
                folderService.deleteFolder(5L, 1L));
    }

    @Test
    @DisplayName("Accessing another user's folder throws ResourceNotFoundException")
    void getFolder_otherUserFolder_throwsNotFound() {
        when(folderRepository.findByIdAndOwner_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                folderService.getFolder(99L, 1L));
    }
}
