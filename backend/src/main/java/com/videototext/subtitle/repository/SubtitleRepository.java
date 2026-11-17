package com.videototext.subtitle.repository;

import com.videototext.subtitle.entity.Subtitle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubtitleRepository extends JpaRepository<Subtitle, UUID> {

    List<Subtitle> findByJobIdOrderByCreatedAtDesc(UUID jobId);

    Optional<Subtitle> findByJobIdAndFormatAndLanguageAndIncludesSpeakers(
            UUID jobId,
            String format,
            String language,
            boolean includesSpeakers
    );

    boolean existsByJobIdAndFormatAndLanguageAndIncludesSpeakers(
            UUID jobId,
            String format,
            String language,
            boolean includesSpeakers
    );

    void deleteByJobId(UUID jobId);
}
