package com.videototext.transcription.service;

import com.videototext.config.RabbitMQConfig;
import com.videototext.transcription.dto.JobProgressEventDto;
import com.videototext.transcription.dto.TranscriptionResultPayload;
import com.videototext.transcription.entity.ProcessingEventRecord;
import com.videototext.transcription.entity.TranscriptionJob;
import com.videototext.transcription.repository.ProcessingEventRepository;
import com.videototext.transcription.repository.TranscriptionJobRepository;
import com.videototext.video.entity.Video;
import com.videototext.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptionEventConsumer {

    private final TranscriptionJobRepository jobRepository;
    private final ProcessingEventRepository eventRepository;
    private final VideoRepository videoRepository;
    private final SseEmitterService sseEmitterService;

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.PROCESSING_EVENT_QUEUE)
    public void handleProcessingEvent(JobProgressEventDto event) {
        log.debug("Received processing event for job {}: stage={}, progress={}%",
                event.getJobId(), event.getStage(), event.getProgressPercent());

        Optional<TranscriptionJob> jobOpt = jobRepository.findById(event.getJobId());
        if (jobOpt.isEmpty()) {
            log.warn("Job not found for event: {}", event.getJobId());
            return;
        }

        TranscriptionJob job = jobOpt.get();
        job.setCurrentStage(event.getStage());
        job.setProgressPercent(event.getProgressPercent());
        if (event.getTotalChunks() > 0) {
            job.setTotalChunks(event.getTotalChunks());
        }
        if (event.getCompletedChunks() > 0) {
            job.setCompletedChunks(event.getCompletedChunks());
        }
        if (job.getStartedAt() == null && !"QUEUED".equalsIgnoreCase(event.getStage())) {
            job.setStartedAt(Instant.now());
            job.setStatus("PROCESSING");
        }

        jobRepository.save(job);

        // Record history in processing_events
        ProcessingEventRecord record = ProcessingEventRecord.builder()
                .job(job)
                .stage(event.getStage())
                .progressPercent(event.getProgressPercent())
                .message(event.getMessage())
                .createdAt(event.getTimestamp() != null ? event.getTimestamp() : Instant.now())
                .build();
        eventRepository.save(record);

        // Broadcast to SSE clients
        sseEmitterService.broadcastProgress(event.getJobId(), event);
    }

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.TRANSCRIPTION_RESULT_QUEUE)
    public void handleTranscriptionResult(TranscriptionResultPayload result) {
        log.info("Received transcription result for job {}: status={}", result.getJobId(), result.getStatus());

        Optional<TranscriptionJob> jobOpt = jobRepository.findById(result.getJobId());
        if (jobOpt.isEmpty()) {
            log.warn("Job not found for result: {}", result.getJobId());
            return;
        }

        TranscriptionJob job = jobOpt.get();
        job.setStatus(result.getStatus());
        job.setCompletedAt(result.getCompletedAt() != null ? result.getCompletedAt() : Instant.now());
        if (result.getErrorMessage() != null) {
            job.setErrorMessage(result.getErrorMessage());
        }
        if ("COMPLETED".equalsIgnoreCase(result.getStatus())) {
            job.setProgressPercent(100.0f);
            job.setCurrentStage("COMPLETED");

            // Update video status to READY
            Video video = job.getVideo();
            if (video != null) {
                video.setStatus("READY");
                videoRepository.save(video);
            }
        } else if ("FAILED".equalsIgnoreCase(result.getStatus())) {
            job.setCurrentStage("FAILED");
        }

        if (job.getStartedAt() != null && job.getCompletedAt() != null) {
            job.setProcessingDurationMs(job.getCompletedAt().toEpochMilli() - job.getStartedAt().toEpochMilli());
        }

        jobRepository.save(job);

        // Final completion event broadcast
        sseEmitterService.broadcastCompletion(
                result.getJobId(),
                result.getStatus(),
                result.getErrorMessage() != null ? result.getErrorMessage() : "Transcription finalized successfully."
        );
    }
}
