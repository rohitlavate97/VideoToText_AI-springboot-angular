package com.videototext.transcription.repository;

import com.videototext.transcription.entity.TranscriptRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TranscriptRevisionRepository extends JpaRepository<TranscriptRevision, UUID> {

    List<TranscriptRevision> findByJobIdOrderByCreatedAtDesc(UUID jobId);

    List<TranscriptRevision> findBySegmentIdOrderByCreatedAtDesc(UUID segmentId);
}
