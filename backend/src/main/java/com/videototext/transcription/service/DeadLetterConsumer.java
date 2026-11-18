package com.videototext.transcription.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videototext.config.RabbitMQConfig;
import com.videototext.security.audit.SecurityAuditService;
import com.videototext.transcription.dto.DeadLetterRecordDto;
import com.videototext.transcription.entity.ProcessingEventRecord;
import com.videototext.transcription.entity.TranscriptionJob;
import com.videototext.transcription.repository.ProcessingEventRepository;
import com.videototext.transcription.repository.TranscriptionJobRepository;
import com.videototext.video.entity.Video;
import com.videototext.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeadLetterConsumer {

    private final TranscriptionJobRepository jobRepository;
    private final VideoRepository videoRepository;
    private final ProcessingEventRepository eventRepository;
    private final SseEmitterService sseEmitterService;
    private final SecurityAuditService securityAuditService;
    private final ObjectMapper objectMapper;

    private final List<DeadLetterRecordDto> recentDeadLetters = new CopyOnWriteArrayList<>();
    private static final int MAX_HISTORY = 100;

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.DLQ_QUEUE)
    public void processDeadLetter(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        log.error("Received dead-letter message from queue {}: routingKey={}, bodySnippet={}",
                RabbitMQConfig.DLQ_QUEUE, routingKey, body.length() > 200 ? body.substring(0, 200) : body);

        UUID jobId = null;
        String reason = "Message unroutable or rejected into dead-letter exchange (DLQ)";

        // Extract death reason if available from x-death header
        List<?> xDeath = (List<?>) message.getMessageProperties().getHeaders().get("x-death");
        if (xDeath != null && !xDeath.isEmpty()) {
            reason = "Dead-lettered via x-death: " + xDeath.get(0).toString();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("jobId")) {
                jobId = UUID.fromString(root.get("jobId").asText());
            }
        } catch (Exception ex) {
            log.warn("Could not parse JSON payload from dead letter: {}", ex.getMessage());
        }

        DeadLetterRecordDto recordDto = DeadLetterRecordDto.builder()
                .jobId(jobId)
                .originalRoutingKey(routingKey)
                .reason(reason)
                .payloadSnippet(body.length() > 500 ? body.substring(0, 500) + "..." : body)
                .receivedAt(Instant.now())
                .build();

        recordDeadLetter(recordDto);

        if (jobId != null) {
            handleFailedJob(jobId, reason);
        }
    }

    private void handleFailedJob(UUID jobId, String reason) {
        Optional<TranscriptionJob> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            log.warn("Dead letter contains unknown job ID: {}", jobId);
            return;
        }

        TranscriptionJob job = jobOpt.get();
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
                "DLQ_MESSAGE_PROCESSED",
                job.getUser().getId().toString(),
                jobId.toString(),
                null,
                "FAILED",
                reason
        );

        ProcessingEventRecord event = ProcessingEventRecord.builder()
                .job(job)
                .eventType("FAILED")
                .stage("FAILED")
                .progressPercent(job.getProgressPercent())
                .message("Dead-letter queue consumer failed job: " + reason)
                .createdAt(Instant.now())
                .build();
        eventRepository.save(event);

        sseEmitterService.broadcastCompletion(jobId, "FAILED", reason);
    }

    private void recordDeadLetter(DeadLetterRecordDto recordDto) {
        if (recentDeadLetters.size() >= MAX_HISTORY) {
            recentDeadLetters.remove(0);
        }
        recentDeadLetters.add(recordDto);
    }

    public List<DeadLetterRecordDto> getRecentDeadLetters() {
        List<DeadLetterRecordDto> copy = new ArrayList<>(recentDeadLetters);
        Collections.reverse(copy);
        return copy;
    }
}
