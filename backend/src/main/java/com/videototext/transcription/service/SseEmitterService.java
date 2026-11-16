package com.videototext.transcription.service;

import com.videototext.transcription.dto.JobProgressEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class SseEmitterService {

    // 30 minute timeout for long transcription jobs
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID jobId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.computeIfAbsent(jobId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(jobId, emitter));
        emitter.onTimeout(() -> {
            log.debug("SSE emitter timed out for jobId: {}", jobId);
            removeEmitter(jobId, emitter);
        });
        emitter.onError(ex -> {
            log.debug("SSE emitter error for jobId: {} ({})", jobId, ex.getMessage());
            removeEmitter(jobId, emitter);
        });

        // Send initial connect event
        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(Map.of("message", "Connected to progress stream for job " + jobId)));
        } catch (IOException e) {
            log.warn("Failed to send initial SSE ping for jobId: {}", jobId, e);
            removeEmitter(jobId, emitter);
        }

        return emitter;
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
                        .data(Map.of("jobId", jobId, "status", status, "message", message)));
                emitter.complete();
            } catch (Exception e) {
                removeEmitter(jobId, emitter);
            }
        }
        emitters.remove(jobId);
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
