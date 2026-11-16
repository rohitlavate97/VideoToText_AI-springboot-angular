package com.videototext.translation.repository;

import com.videototext.translation.entity.Translation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TranslationRepository extends JpaRepository<Translation, UUID> {

    List<Translation> findByJobIdOrderByTargetLanguageAsc(UUID jobId);

    Optional<Translation> findByJobIdAndTargetLanguage(UUID jobId, String targetLanguage);

    void deleteByJobId(UUID jobId);
}
