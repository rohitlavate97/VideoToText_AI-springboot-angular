package com.videototext.transcription.service;

import com.videototext.transcription.dto.JobProgressEventDto;
import com.videototext.transcription.dto.ProcessingEventResponse;
import com.videototext.transcription.entity.TranscriptionJob;
import com.videototext.transcription.repository.ProcessingEventRepository;
import com.videototext.transcription.repository.TranscriptionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseEmitterService {

    // 30 minute timeout for long transcription jobs
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;
    // 3 second browser reconnect hint
    private static final long RECONNECT_TIME_MS = 3000L;

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final TranscriptionJobRepository jobRepository;
    private final ProcessingEventRepository eventRepository;

    public SseEmitter subscribe(UUID jobId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.computeIfAbsent(jobId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(jobId, emitter));
        emitter.onTimeout(() -> {
            log.debug("SSE emitter timed out for jobId: {}", jobId);
            removeEmitter(jobId, emitter);
        });
        emitter.onError(ex -> {
            log.debug("SSE emitter error for jobId: {} ({})", jobId, ex != null ? ex.getMessage() : "unknown");
            removeEmitter(jobId, emitter);
        });

        // 1. Send initial connect event with reconnection hint
        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .id(UUID.randomUUID().toString())
                    .reconnectTime(RECONNECT_TIME_MS)
                    .data(Map.of(
                            "jobId", jobId,
                            "message", "Connected to progress stream for job " + jobId,
                            "status", "CONNECTED"
                    )));
        } catch (IOException e) {
            log.warn("Failed to send initial SSE ping for jobId: {}", jobId, e);
            removeEmitter(jobId, emitter);
            return emitter;
        }

        // 2. Immediate state push: sync current job state if job already exists
        syncCurrentJobState(jobId, emitter);

        return emitter;
    }

    private void syncCurrentJobState(UUID jobId, SseEmitter emitter) {
        try {
            Optional<TranscriptionJob> jobOpt = jobRepository.findById(jobId);
            if (jobOpt.isEmpty()) {
                return;
            }

            TranscriptionJob job = jobOpt.get();
            String status = job.getStatus();

            if ("COMPLETED".equalsIgnoreCase(status)) {
                emitter.send(SseEmitter.event()
                        .name("COMPLETED")
                        .id(UUID.randomUUID().toString())
                        .data(Map.of(
                                "jobId", jobId,
                                "status", "COMPLETED",
                                "message", "Transcription finalized successfully."
                        )));
                emitter.complete();
            } else if ("FAILED".equalsIgnoreCase(status)) {
                emitter.send(SseEmitter.event()
                        .name("FAILED")
                        .id(UUID.randomUUID().toString())
                        .data(Map.of(
                                "jobId", jobId,
                                "status", "FAILED",
                                "message", job.getErrorMessage() != null ? job.getErrorMessage() : "Transcription job failed."
                        )));
                emitter.complete();
            } else if ("CANCELLED".equalsIgnoreCase(status)) {
                emitter.send(SseEmitter.event()
                        .name("COMPLETED")
                        .id(UUID.randomUUID().toString())
                        .data(Map.of(
                                "jobId", jobId,
                                "status", "CANCELLED",
                                "message", "Transcription job was cancelled."
                        )));
                emitter.complete();
            } else {
                // In-flight progress sync
                JobProgressEventDto progress = JobProgressEventDto.builder()
                        .jobId(jobId)
                        .stage(job.getCurrentStage())
                        .progressPercent(job.getProgressPercent())
                        .completedChunks(job.getCompletedChunks())
                        .totalChunks(job.getTotalChunks())
                        .retryAttempt(job.getRetryCount())
                        .maxRetries(job.getMaxRetries())
                        .errorMessage(job.getErrorMessage())
                        .message("Current progress: " + job.getCurrentStage() + " (" + String.format("%.1f", job.getProgressPercent()) + "%)")
                        .timestamp(job.getUpdatedAt() != null ? job.getUpdatedAt() : job.getCreatedAt())
                        .build();

                emitter.send(SseEmitter.event()
                        .name("PROGRESS")
                        .id(UUID.randomUUID().toString())
                        .data(progress));
            }
        } catch (Exception ex) {
            log.warn("Failed to synchronize initial state for jobId {}: {}", jobId, ex.getMessage());
            removeEmitter(jobId, emitter);
        }
    }

    public void broadcastProgress(UUID jobId, JobProgressEventDto event) {
        List<SseEmitter> jobEmitters = emitters.get(jobId);
        if (jobEmitters == null || jobEmitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : jobEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("PROGRESS")
                        .id(UUID.randomUUID().toString())
                        .reconnectTime(RECONNECT_TIME_MS)
                        .data(event));
            } catch (Exception e) {
                log.debug("Failed sending SSE to emitter for jobId: {}, removing", jobId);
                removeEmitter(jobId, emitter);
            }
        }
    }

    public void broadcastCompletion(UUID jobId, String status, String message) {
        List<SseEmitter> jobEmitters = emitters.get(jobId);
        if (jobEmitters == null || jobEmitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : jobEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("COMPLETED")
                        .id(UUID.randomUUID().toString())
                        .data(Map.of("jobId", jobId, "status", status, "message", message)));
                emitter.complete();
            } catch (Exception e) {
                removeEmitter(jobId, emitter);
            }
        }
        emitters.remove(jobId);
    }

    /**
     * Periodic keepalive heartbeat every 25 seconds to prevent browser/proxy connection timeouts.
     */
    @Scheduled(fixedRate = 25000)
    public void sendHeartbeats() {
        if (emitters.isEmpty()) {
            return;
        }

        emitters.forEach((jobId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event()
                            .comment("keep-alive"));
                } catch (Exception e) {
                    removeEmitter(jobId, emitter);
                }
            }
        });
    }

    public List<ProcessingEventResponse> getEventHistory(UUID jobId) {
        return eventRepository.findByJobIdOrderByCreatedAtAsc(jobId)
                .stream()
                .map(ProcessingEventResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public int getSubscriberCount(UUID jobId) {
        List<SseEmitter> list = emitters.get(jobId);
        return list != null ? list.size() : 0;
    }

    private void removeEmitter(UUID jobId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(jobId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(jobId);
            }
        }
    }
}
