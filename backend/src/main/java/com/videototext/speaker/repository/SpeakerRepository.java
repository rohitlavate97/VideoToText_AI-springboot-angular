package com.videototext.speaker.repository;

import com.videototext.speaker.entity.Speaker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpeakerRepository extends JpaRepository<Speaker, UUID> {

    List<Speaker> findByJobIdOrderBySpeakerLabelAsc(UUID jobId);

    Optional<Speaker> findByIdAndJobId(UUID id, UUID jobId);

    Optional<Speaker> findByJobIdAndSpeakerLabel(UUID jobId, String speakerLabel);
}
