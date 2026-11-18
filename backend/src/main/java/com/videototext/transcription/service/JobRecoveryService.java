package com.videototext.transcription.service;

import com.videototext.common.exception.BadRequestException;
import com.videototext.common.exception.ForbiddenException;
import com.videototext.common.exception.ResourceNotFoundException;
import com.videototext.security.audit.SecurityAuditService;
import com.videototext.transcription.dto.JobProgressEventDto;
import com.videototext.transcription.dto.TranscriptionJobResponse;
import com.videototext.transcription.dto.TranscriptionMessagePayload;
import com.videototext.transcription.entity.ProcessingEventRecord;
import com.videototext.transcription.entity.TranscriptionJob;
import com.videototext.transcription.repository.ProcessingEventRepository;
import com.videototext.transcription.repository.TranscriptionJobRepository;
import com.videototext.video.entity.Video;
import com.videototext.video.repository.VideoRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobRecoveryService {

    private final TranscriptionJobRepository jobRepository;
    private final VideoRepository videoRepository;
    private final TranscriptionProducer transcriptionProducer;
    private final SseEmitterService sseEmitterService;
    private final ProcessingEventRepository eventRepository;
    private final SecurityAuditService securityAuditService;

    @Getter
    @Value("${app.reliability.stuck-job-timeout-minutes:15}")
    private int stuckJobTimeoutMinutes;

    @Getter
    @Value("${app.reliability.max-job-retries:3}")
    private int defaultMaxRetries;

    @Getter
    @Value("${app.reliability.recovery-enabled:true}")
    private boolean recoveryEnabled;

    private static final List<String> STUCK_CANDIDATE_STATUSES = List.of("QUEUED", "PROCESSING", "RETRYING");

    /**
     * Periodic sweeper to detect and recover stuck transcription jobs.
     */
    @Scheduled(fixedRateString = "${app.reliability.sweep-interval-ms:60000}")
    @Transactional
    public int sweepStuckJobs() {
        if (!recoveryEnabled) {
            return 0;
        }

        Instant cutoff = Instant.now().minus(Duration.ofMinutes(stuckJobTimeoutMinutes));
        List<TranscriptionJob> stuckJobs = jobRepository.findStuckJobs(STUCK_CANDIDATE_STATUSES, cutoff);

        if (stuckJobs.isEmpty()) {
            return 0;
        }

        log.info("Job recovery sweeper found {} stuck jobs inactive since {}", stuckJobs.size(), cutoff);
        int recoveredCount = 0;

        for (TranscriptionJob job : stuckJobs) {
            try {
                recoverOrFailJob(job, "Automatic sweeper: Job inactive for more than " + stuckJobTimeoutMinutes + " minutes");
                recoveredCount++;
            } catch (Exception ex) {
                log.error("Failed to recover stuck job {}: {}", job.getId(), ex.getMessage(), ex);
            }
        }

        return recoveredCount;
    }

    /**
     * Recovers or fails a stuck job based on retry count.
     */
    @Transactional
    public void recoverOrFailJob(TranscriptionJob job, String reason) {
        int maxRetries = job.getMaxRetries() > 0 ? job.getMaxRetries() : defaultMaxRetries;

        if (job.getRetryCount() < maxRetries) {
            retryJob(job, reason);
        } else {
            failJob(job, "Exceeded maximum retry attempts (" + maxRetries + "). " + reason);
        }
    }

    /**
     * Manual recovery trigger for a specific job.
     */
    @Transactional
    public TranscriptionJobResponse recoverJobManual(UUID jobId, UUID userId, boolean force) {
        TranscriptionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("TranscriptionJob", "id", jobId));

        if (!job.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to recover this job");
        }

        if ("COMPLETED".equalsIgnoreCase(job.getStatus()) && !force) {
            throw new BadRequestException("Job has already completed successfully. Use force=true to override.");
        }

        retryJob(job, "Manual recovery requested by user");
        return TranscriptionJobResponse.fromEntity(job);
    }

    private void retryJob(TranscriptionJob job, String reason) {
        int nextRetryCount = job.getRetryCount() + 1;
        job.setRetryCount(nextRetryCount);
        job.setStatus("RETRYING");
        job.setCurrentStage("RETRYING");
        job.setErrorMessage(reason);
        job = jobRepository.save(job);

        log.warn("Recovering stuck job {}: retry {} of {} (reason: {})",
                job.getId(), nextRetryCount, job.getMaxRetries(), reason);

        // Security / Audit logging
        securityAuditService.logSecurityEvent(
                "JOB_RECOVERY",
                job.getUser().getId().toString(),
                job.getId().toString(),
                null,
                "RETRYING",
                "Attempt " + nextRetryCount + " of " + job.getMaxRetries() + ": " + reason
        );

        // Store processing event history
        ProcessingEventRecord record = ProcessingEventRecord.builder()
                .job(job)
                .eventType("RETRYING")
                .stage("RETRYING")
                .progressPercent(job.getProgressPercent())
                .message(reason)
                .metadataJson(String.format("{\"retryAttempt\":%d,\"maxRetries\":%d}", nextRetryCount, job.getMaxRetries()))
                .createdAt(Instant.now())
                .build();
        eventRepository.save(record);

        // Broadcast to SSE clients
        JobProgressEventDto event = JobProgressEventDto.builder()
                .jobId(job.getId())
                .stage("RETRYING")
                .progressPercent(job.getProgressPercent())
                .retryAttempt(nextRetryCount)
                .maxRetries(job.getMaxRetries())
                .message(reason)
                .timestamp(Instant.now())
                .build();
        sseEmitterService.broadcastProgress(job.getId(), event);

        // Re-dispatch task to RabbitMQ
        Video video = job.getVideo();
        if (video != null) {
            video.setStatus("PROCESSING");
            videoRepository.save(video);
        }

        TranscriptionMessagePayload payload = TranscriptionMessagePayload.builder()
                .jobId(job.getId())
                .videoId(video != null ? video.getId() : null)
                .userId(job.getUser().getId())
                .s3Key(video != null ? video.getStorageKey() : null)
                .language(job.getSourceLanguage())
                .task("transcribe")
                .engine(job.getAsrEngine())
                .model(job.getAsrModel())
                .computeType(job.getComputeType())
                .createdAt(Instant.now())
                .build();

        transcriptionProducer.publishJobRequest(payload);
    }

    private void failJob(TranscriptionJob job, String reason) {
        log.error("Failing stuck job {}: {}", job.getId(), reason);

        job.setStatus("FAILED");
        job.setCurrentStage("FAILED");
        job.setErrorMessage(reason);
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);

        Video video = job.getVideo();
        if (video != null) {
            video.setStatus("FAILED");
            videoRepository.save(video);
        }

        securityAuditService.logSecurityEvent(
                "JOB_FAILED_MAX_RETRIES",
                job.getUser().getId().toString(),
                job.getId().toString(),
                null,
                "FAILED",
                reason
        );

        ProcessingEventRecord record = ProcessingEventRecord.builder()
                .job(job)
                .eventType("FAILED")
                .stage("FAILED")
                .progressPercent(job.getProgressPercent())
                .message(reason)
                .createdAt(Instant.now())
                .build();
        eventRepository.save(record);

        sseEmitterService.broadcastCompletion(job.getId(), "FAILED", reason);
    }
}
