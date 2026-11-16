package com.videototext.transcription.repository;

import com.videototext.transcription.entity.TranscriptionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TranscriptionJobRepository extends JpaRepository<TranscriptionJob, UUID> {

    List<TranscriptionJob> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<TranscriptionJob> findByVideoIdOrderByCreatedAtDesc(UUID videoId);

    Optional<TranscriptionJob> findByIdAndUserId(UUID id, UUID userId);

    Optional<TranscriptionJob> findFirstByVideoIdOrderByCreatedAtDesc(UUID videoId);
}
