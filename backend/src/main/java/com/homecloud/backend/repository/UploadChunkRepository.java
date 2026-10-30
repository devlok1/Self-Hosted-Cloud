package com.homecloud.backend.repository;

import com.homecloud.backend.entity.UploadChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UploadChunkRepository extends JpaRepository<UploadChunk, Long> {

    Optional<UploadChunk> findBySession_IdAndChunkNumber(String sessionId, Integer chunkNumber);

    List<UploadChunk> findBySession_IdOrderByChunkNumberAsc(String sessionId);

    long countBySession_Id(String sessionId);

    void deleteBySession_Id(String sessionId);
}
