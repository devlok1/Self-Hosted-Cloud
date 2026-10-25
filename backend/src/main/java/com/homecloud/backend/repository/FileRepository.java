package com.homecloud.backend.repository;

import com.homecloud.backend.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<File, Long> {

    List<File> findByOwner_IdOrderByCreatedAtDesc(Long userId);

    List<File> findByOwner_IdAndFolderIsNullOrderByFilenameAsc(Long userId);

    List<File> findByOwner_IdAndFolder_IdOrderByFilenameAsc(Long userId, Long folderId);

    Optional<File> findByIdAndOwner_Id(Long fileId, Long userId);

    List<File> findByOwner_IdAndFolder_Id(Long userId, Long folderId);
}
