package com.homecloud.backend.repository;

import com.homecloud.backend.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<File, Long> {

    List<File> findByOwner_IdOrderByCreatedAtDesc(Long userId);

    List<File> findByOwner_IdAndFolderIsNullOrderByFilenameAsc(Long userId);

    List<File> findByOwner_IdAndFolder_IdOrderByFilenameAsc(Long userId, Long folderId);

    Optional<File> findByIdAndOwner_Id(Long fileId, Long userId);

    List<File> findByOwner_IdAndFolder_Id(Long userId, Long folderId);

    List<File> findByOwner_IdAndFilenameContainingIgnoreCaseOrderByFilenameAsc(Long userId, String query);

    long countByOwner_Id(Long userId);

    @Query("SELECT COALESCE(SUM(f.size), 0) FROM File f WHERE f.owner.id = :userId")
    long sumSizeByOwner_Id(@Param("userId") Long userId);
}
