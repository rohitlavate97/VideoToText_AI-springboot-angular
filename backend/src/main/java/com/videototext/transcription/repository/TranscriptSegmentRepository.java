package com.videototext.transcription.repository;

import com.videototext.transcription.entity.TranscriptSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TranscriptSegmentRepository extends JpaRepository<TranscriptSegment, UUID> {

    List<TranscriptSegment> findByJobIdOrderBySegmentIndexAsc(UUID jobId);

    void deleteByJobId(UUID jobId);
}
