package com.videototext.transcription.repository;

import com.videototext.transcription.entity.TranscriptionChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TranscriptionChunkRepository extends JpaRepository<TranscriptionChunk, UUID> {

    List<TranscriptionChunk> findByJobIdOrderByChunkIndexAsc(UUID jobId);

    Optional<TranscriptionChunk> findByJobIdAndChunkIndex(UUID jobId, int chunkIndex);
}
