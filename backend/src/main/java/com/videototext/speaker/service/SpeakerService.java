package com.videototext.speaker.service;

import com.videototext.common.exception.ForbiddenException;
import com.videototext.common.exception.ResourceNotFoundException;
import com.videototext.speaker.dto.SpeakerResponse;
import com.videototext.speaker.dto.UpdateSpeakerRequest;
import com.videototext.speaker.entity.Speaker;
import com.videototext.speaker.repository.SpeakerRepository;
import com.videototext.transcription.entity.TranscriptionJob;
import com.videototext.transcription.repository.TranscriptionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpeakerService {

    private final SpeakerRepository speakerRepository;
    private final TranscriptionJobRepository jobRepository;

    @Transactional(readOnly = true)
    public List<SpeakerResponse> getSpeakers(UUID userId, UUID jobId) {
        TranscriptionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("TranscriptionJob", "id", jobId));

        if (!job.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to view speakers for this job");
        }

        return speakerRepository.findByJobIdOrderBySpeakerLabelAsc(jobId)
                .stream()
                .map(SpeakerResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public SpeakerResponse updateSpeaker(UUID userId, UUID jobId, UUID speakerId, UpdateSpeakerRequest request) {
        TranscriptionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("TranscriptionJob", "id", jobId));

        if (!job.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to update speakers for this job");
        }

        Speaker speaker = speakerRepository.findByIdAndJobId(speakerId, jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Speaker", "id", speakerId));

        speaker.setDisplayName(request.getDisplayName().trim());
        speaker = speakerRepository.save(speaker);
        log.info("Updated speaker {} display name to '{}' for job {}", speakerId, speaker.getDisplayName(), jobId);

        return SpeakerResponse.fromEntity(speaker);
    }

    @Transactional
    public Speaker createOrUpdateSpeaker(
            TranscriptionJob job,
            String speakerLabel,
            String displayName,
            int durationMs,
            String embeddingVector) {
        Optional<Speaker> existing = speakerRepository.findByJobIdAndSpeakerLabel(job.getId(), speakerLabel);
        if (existing.isPresent()) {
            Speaker s = existing.get();
            s.setTotalSpeakingTimeMs(s.getTotalSpeakingTimeMs() + durationMs);
            s.setSegmentCount(s.getSegmentCount() + 1);
            if (embeddingVector != null && !embeddingVector.isBlank()) {
                s.setEmbeddingVector(embeddingVector);
            }
            return speakerRepository.save(s);
        } else {
            Speaker s = Speaker.builder()
                    .job(job)
                    .speakerLabel(speakerLabel)
                    .displayName(displayName != null ? displayName : speakerLabel)
                    .totalSpeakingTimeMs(durationMs)
                    .segmentCount(1)
                    .embeddingVector(embeddingVector)
                    .build();
            return speakerRepository.save(s);
        }
    }
}
