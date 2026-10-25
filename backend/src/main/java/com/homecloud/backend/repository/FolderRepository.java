package com.homecloud.backend.repository;

import com.homecloud.backend.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    List<Folder> findByOwner_IdAndParent_IdOrderByNameAsc(Long userId, Long parentId);

    List<Folder> findByOwner_IdAndParentIsNullOrderByNameAsc(Long userId);

    Optional<Folder> findByIdAndOwner_Id(Long folderId, Long userId);

    boolean existsByNameAndOwner_IdAndParent_Id(String name, Long userId, Long parentId);

    boolean existsByNameAndOwner_IdAndParentIsNull(String name, Long userId);
}
