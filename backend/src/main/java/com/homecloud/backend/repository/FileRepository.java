package com.homecloud.backend.repository;

import com.homecloud.backend.entity.File;
import com.homecloud.backend.entity.FileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<File, Long> {

    List<File> findByOwner_IdOrderByCreatedAtDesc(Long userId);

    List<File> findByOwner_IdAndFolderIsNullOrderByFilenameAsc(Long userId);

    List<File> findByOwner_IdAndFolderIsNullAndStatusOrderByFilenameAsc(Long userId, FileStatus status);

    List<File> findByOwner_IdAndFolder_IdOrderByFilenameAsc(Long userId, Long folderId);

    List<File> findByOwner_IdAndFolder_IdAndStatusOrderByFilenameAsc(Long userId, Long folderId, FileStatus status);

    Optional<File> findByIdAndOwner_Id(Long fileId, Long userId);

    Optional<File> findByIdAndOwner_IdAndStatus(Long fileId, Long userId, FileStatus status);

    List<File> findByOwner_IdAndFolder_Id(Long userId, Long folderId);

    List<File> findByOwner_IdAndFolder_IdAndStatus(Long userId, Long folderId, FileStatus status);

    List<File> findByOwner_IdAndFilenameContainingIgnoreCaseOrderByFilenameAsc(Long userId, String query);

    List<File> findByOwner_IdAndFilenameContainingIgnoreCaseAndStatusOrderByFilenameAsc(Long userId, String query, FileStatus status);

    long countByOwner_Id(Long userId);

    long countByOwner_IdAndStatus(Long userId, FileStatus status);

    @Query("SELECT COALESCE(SUM(f.size), 0) FROM File f WHERE f.owner.id = :userId AND f.status = 'ACTIVE'")
    long sumSizeByOwner_Id(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(f.size), 0) FROM File f WHERE f.owner.id = :userId AND f.status = :status")
    long sumSizeByOwner_IdAndStatus(@Param("userId") Long userId, @Param("status") FileStatus status);

    List<File> findByStatus(FileStatus status);
}
