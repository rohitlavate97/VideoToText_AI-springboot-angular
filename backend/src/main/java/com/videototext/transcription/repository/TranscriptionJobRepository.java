package com.videototext.transcription.repository;

import com.videototext.transcription.entity.TranscriptionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TranscriptionJobRepository extends JpaRepository<TranscriptionJob, UUID> {

    List<TranscriptionJob> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserId(UUID userId);

    long countByUserIdAndStatus(UUID userId, String status);

    List<TranscriptionJob> findByVideoIdOrderByCreatedAtDesc(UUID videoId);

    Optional<TranscriptionJob> findByIdAndUserId(UUID id, UUID userId);

    Optional<TranscriptionJob> findFirstByVideoIdOrderByCreatedAtDesc(UUID videoId);

    @Query("SELECT COUNT(j) FROM TranscriptionJob j WHERE j.user.id = :userId AND j.status IN :statuses")
    long countActiveJobsByUserId(@Param("userId") UUID userId, @Param("statuses") Collection<String> statuses);

    @Query("SELECT j FROM TranscriptionJob j WHERE j.status IN :statuses AND j.updatedAt < :cutoff")
    List<TranscriptionJob> findStuckJobs(@Param("statuses") Collection<String> statuses, @Param("cutoff") Instant cutoff);
}
