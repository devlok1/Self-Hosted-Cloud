package com.homecloud.backend.repository;

import com.homecloud.backend.entity.UploadSession;
import com.homecloud.backend.entity.UploadSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UploadSessionRepository extends JpaRepository<UploadSession, String> {

    Optional<UploadSession> findByIdAndOwner_Id(String id, Long ownerId);

    long countByOwner_IdAndStatusIn(Long ownerId, Collection<UploadSessionStatus> statuses);

    List<UploadSession> findByExpiresAtBeforeAndStatusNotIn(LocalDateTime dateTime, Collection<UploadSessionStatus> statuses);

    List<UploadSession> findByStatusAndUpdatedAtBefore(UploadSessionStatus status, LocalDateTime dateTime);
}
