package com.videototext.transcription.repository;

import com.videototext.transcription.entity.TranscriptSegment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TranscriptSegmentRepository extends JpaRepository<TranscriptSegment, UUID> {

    List<TranscriptSegment> findByJobIdOrderBySegmentIndexAsc(UUID jobId);

    Page<TranscriptSegment> findByJobIdOrderBySegmentIndexAsc(UUID jobId, Pageable pageable);

    List<TranscriptSegment> findByJobIdAndTextContainingIgnoreCaseOrderBySegmentIndexAsc(UUID jobId, String text);

    Page<TranscriptSegment> findByJobIdAndTextContainingIgnoreCaseOrderBySegmentIndexAsc(UUID jobId, String text, Pageable pageable);

    Optional<TranscriptSegment> findByIdAndJobId(UUID id, UUID jobId);

    void deleteByJobId(UUID jobId);
}
