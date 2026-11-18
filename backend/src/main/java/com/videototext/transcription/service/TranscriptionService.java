package com.videototext.transcription.service;

import com.videototext.common.dto.PageResponse;
import com.videototext.common.exception.BadRequestException;
import com.videototext.common.exception.ForbiddenException;
import com.videototext.common.exception.ResourceNotFoundException;
import com.videototext.speaker.entity.Speaker;
import com.videototext.speaker.repository.SpeakerRepository;
import com.videototext.transcription.dto.CreateTranscriptionRequest;
import com.videototext.transcription.dto.ReassignSpeakerRequest;
import com.videototext.transcription.dto.TranscriptResponse;
import com.videototext.transcription.dto.TranscriptRevisionResponse;
import com.videototext.transcription.dto.TranscriptSearchResponse;
import com.videototext.transcription.dto.TranscriptSegmentResponse;
import com.videototext.transcription.dto.TranscriptionJobResponse;
import com.videototext.transcription.dto.TranscriptionMessagePayload;
import com.videototext.transcription.dto.UpdateTranscriptSegmentRequest;
import com.videototext.transcription.entity.TranscriptRevision;
import com.videototext.transcription.entity.TranscriptSegment;
import com.videototext.transcription.entity.TranscriptionJob;
import com.videototext.transcription.repository.TranscriptRevisionRepository;
import com.videototext.transcription.repository.TranscriptSegmentRepository;
import com.videototext.transcription.repository.TranscriptionJobRepository;
import com.videototext.user.entity.User;
import com.videototext.user.repository.UserRepository;
import com.videototext.video.entity.Video;
import com.videototext.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptionService {

    private final TranscriptionJobRepository jobRepository;
    private final TranscriptSegmentRepository segmentRepository;
    private final VideoRepository videoRepository;
    private final UserRepository userRepository;
    private final TranscriptionProducer transcriptionProducer;
    private final SpeakerRepository speakerRepository;
    private final TranscriptRevisionRepository revisionRepository;
    private final SseEmitterService sseEmitterService;
    private final com.videototext.security.quota.UploadQuotaService uploadQuotaService;
    private final com.videototext.security.audit.SecurityAuditService securityAuditService;

    @Transactional
    public TranscriptionJobResponse createJob(UUID userId, CreateTranscriptionRequest request) {
        log.info("Creating transcription job for user: {}, video: {}", userId, request.getVideoId());
        uploadQuotaService.checkJobQuota(userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Video video = videoRepository.findById(request.getVideoId())
                .orElseThrow(() -> new ResourceNotFoundException("Video", "id", request.getVideoId()));

        if (!video.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have access to video: " + request.getVideoId());
        }

        // Initialize TranscriptionJob
        TranscriptionJob job = TranscriptionJob.builder()
                .video(video)
                .user(user)
                .status("QUEUED")
                .asrEngine(request.getEngine() != null ? request.getEngine() : "faster-whisper")
                .asrModel(request.getModel() != null ? request.getModel() : "small")
                .sourceLanguage(request.getLanguage() != null ? request.getLanguage() : "auto")
                .computeType(request.getComputeType() != null ? request.getComputeType() : "int8_float16")
                .currentStage("INITIALIZED")
                .progressPercent(0.0f)
                .build();

        job = jobRepository.save(job);

        // Update video status to PROCESSING
        video.setStatus("PROCESSING");
        videoRepository.save(video);

        // Build and dispatch message to RabbitMQ
        TranscriptionMessagePayload payload = TranscriptionMessagePayload.builder()
                .jobId(job.getId())
                .videoId(video.getId())
                .userId(user.getId())
                .s3Key(video.getStorageKey())
                .language(job.getSourceLanguage())
                .task("transcribe")
                .engine(job.getAsrEngine())
                .model(job.getAsrModel())
                .computeType(job.getComputeType())
                .enableDiarization(request.isEnableDiarization())
                .maxSpeakers(request.getMaxSpeakers())
                .targetLanguages(request.getTargetLanguages())
                .generateSummary(request.isGenerateSummary())
                .createdAt(Instant.now())
                .build();

        transcriptionProducer.publishJobRequest(payload);

        return TranscriptionJobResponse.fromEntity(job);
    }

    @Transactional(readOnly = true)
    public TranscriptionJobResponse getJob(UUID userId, UUID jobId) {
        TranscriptionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("TranscriptionJob", "id", jobId));

        if (!job.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to view this job");
        }

        return TranscriptionJobResponse.fromEntity(job);
    }

    @Transactional(readOnly = true)
    public List<TranscriptionJobResponse> getUserJobs(UUID userId) {
        return jobRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(TranscriptionJobResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TranscriptionJobResponse> getVideoJobs(UUID userId, UUID videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResourceNotFoundException("Video", "id", videoId));

        if (!video.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have access to video: " + videoId);
        }

        return jobRepository.findByVideoIdOrderByCreatedAtDesc(videoId)
                .stream()
                .map(TranscriptionJobResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TranscriptSegmentResponse> getJobSegments(UUID userId, UUID jobId) {
        TranscriptionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("TranscriptionJob", "id", jobId));

        if (!job.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to view segments for this job");
        }

        Map<UUID, Speaker> speakerMap = speakerRepository.findByJobIdOrderBySpeakerLabelAsc(jobId)
                .stream()
                .collect(Collectors.toMap(Speaker::getId, Function.identity(), (a, b) -> a));

        return segmentRepository.findByJobIdOrderBySegmentIndexAsc(jobId)
                .stream()
                .map(seg -> TranscriptSegmentResponse.fromEntity(seg, speakerMap))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<TranscriptSegmentResponse> getJobSegmentsPaginated(UUID userId, UUID jobId, int page, int size) {
        TranscriptionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("TranscriptionJob", "id", jobId));

        if (!job.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to view segments for this job");
        }

        Map<UUID, Speaker> speakerMap = speakerRepository.findByJobIdOrderBySpeakerLabelAsc(jobId)
                .stream()
                .collect(Collectors.toMap(Speaker::getId, Function.identity(), (a, b) -> a));

        Page<TranscriptSegment> segmentPage = segmentRepository.findByJobIdOrderBySegmentIndexAsc(
                jobId, PageRequest.of(page, Math.min(size, 200)));

        List<TranscriptSegmentResponse> responses = segmentPage.getContent()
                .stream()
                .map(seg -> TranscriptSegmentResponse.fromEntity(seg, speakerMap))
                .collect(Collectors.toList());

        return PageResponse.<TranscriptSegmentResponse>builder()
                .content(responses)
                .page(segmentPage.getNumber())
                .size(segmentPage.getSize())
                .totalElements(segmentPage.getTotalElements())
                .totalPages(segmentPage.getTotalPages())
                .first(segmentPage.isFirst())
                .last(segmentPage.isLast())
                .empty(segmentPage.isEmpty())
                .build();
    }

    @Transactional(readOnly = true)
    public TranscriptResponse getTranscript(UUID userId, UUID jobId) {
        TranscriptionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("TranscriptionJob", "id", jobId));

        if (!job.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to view this transcript");
        }

        Map<UUID, Speaker> speakerMap = speakerRepository.findByJobIdOrderBySpeakerLabelAsc(jobId)
                .stream()
                .collect(Collectors.toMap(Speaker::getId, Function.identity(), (a, b) -> a));

        List<TranscriptSegment> segments = segmentRepository.findByJobIdOrderBySegmentIndexAsc(jobId);
        List<TranscriptSegmentResponse> segmentResponses = segments.stream()
                .map(seg -> TranscriptSegmentResponse.fromEntity(seg, speakerMap))
                .collect(Collectors.toList());

        String fullText = segments.stream()
                .map(TranscriptSegment::getText)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining(" "));

        int totalWords = Arrays.stream(fullText.split("\\s+"))
                .filter(w -> !w.isBlank())
                .mapToInt(w -> 1)
                .sum();

        float durationSeconds = job.getVideo() != null && job.getVideo().getDurationSeconds() > 0
                ? (float) job.getVideo().getDurationSeconds()
                : (segments.isEmpty() ? 0f : segments.get(segments.size() - 1).getEndTime());

        return TranscriptResponse.builder()
                .jobId(job.getId())
                .videoId(job.getVideo() != null ? job.getVideo().getId() : null)
                .videoTitle(job.getVideo() != null ? job.getVideo().getOriginalFilename() : null)
                .status(job.getStatus())
                .language(job.getSourceLanguage())
                .durationSeconds(durationSeconds)
                .totalSegments(segments.size())
                .totalWords(totalWords)
                .fullText(fullText)
                .segments(segmentResponses)
                .completedAt(job.getCompletedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public TranscriptSearchResponse searchTranscript(UUID userId, UUID jobId, String query) {
        TranscriptionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("TranscriptionJob", "id", jobId));

        if (!job.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to search transcripts for this job");
        }

        if (query == null || query.trim().isEmpty()) {
            return TranscriptSearchResponse.builder()
                    .query(query)
                    .totalMatches(0)
                    .matches(Collections.emptyList())
                    .build();
        }

        Map<UUID, Speaker> speakerMap = speakerRepository.findByJobIdOrderBySpeakerLabelAsc(jobId)
                .stream()
                .collect(Collectors.toMap(Speaker::getId, Function.identity(), (a, b) -> a));

        List<TranscriptSegment> matchingSegments = segmentRepository
                .findByJobIdAndTextContainingIgnoreCaseOrderBySegmentIndexAsc(jobId, query.trim());

        List<TranscriptSegmentResponse> matchResponses = matchingSegments.stream()
                .map(seg -> TranscriptSegmentResponse.fromEntity(seg, speakerMap))
                .collect(Collectors.toList());

        return TranscriptSearchResponse.builder()
                .query(query.trim())
                .totalMatches(matchResponses.size())
                .matches(matchResponses)
                .build();
    }

    @Transactional(readOnly = true)
    public List<TranscriptRevisionResponse> getRevisions(UUID userId, UUID jobId) {
        TranscriptionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("TranscriptionJob", "id", jobId));

        if (!job.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to view revisions for this job");
        }

        return revisionRepository.findByJobIdOrderByCreatedAtDesc(jobId)
                .stream()
                .map(TranscriptRevisionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public TranscriptSegmentResponse updateSegment(UUID userId, UUID jobId, UUID segmentId, UpdateTranscriptSegmentRequest request) {
        TranscriptionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("TranscriptionJob", "id", jobId));

        if (!job.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to modify segments for this job");
        }

        TranscriptSegment segment = segmentRepository.findByIdAndJobId(segmentId, jobId)
                .orElseThrow(() -> new ResourceNotFoundException("TranscriptSegment", "id", segmentId));

        if (request.getText() != null && !request.getText().equals(segment.getText())) {
            revisionRepository.save(TranscriptRevision.builder()
                    .job(job)
                    .userId(userId)
                    .revisionType("UPDATE_TEXT")
                    .segmentId(segmentId)
                    .fieldChanged("text")
                    .oldValue(segment.getText())
                    .newValue(request.getText())
                    .build());
            segment.setText(request.getText());
        }

        if (request.getStartTime() != null || request.getEndTime() != null) {
            float newStart = request.getStartTime() != null ? request.getStartTime() : segment.getStartTime();
            float newEnd = request.getEndTime() != null ? request.getEndTime() : segment.getEndTime();

            if (newStart < 0 || newEnd <= newStart) {
                throw new BadRequestException("Start time must be non-negative and less than end time");
            }

            if (request.getStartTime() != null && Float.compare(segment.getStartTime(), newStart) != 0) {
                revisionRepository.save(TranscriptRevision.builder()
                        .job(job)
                        .userId(userId)
                        .revisionType("UPDATE_TIMESTAMP")
                        .segmentId(segmentId)
                        .fieldChanged("startTime")
                        .oldValue(String.valueOf(segment.getStartTime()))
                        .newValue(String.valueOf(newStart))
                        .build());
                segment.setStartTime(newStart);
            }

            if (request.getEndTime() != null && Float.compare(segment.getEndTime(), newEnd) != 0) {
                revisionRepository.save(TranscriptRevision.builder()
                        .job(job)
                        .userId(userId)
                        .revisionType("UPDATE_TIMESTAMP")
                        .segmentId(segmentId)
                        .fieldChanged("endTime")
                        .oldValue(String.valueOf(segment.getEndTime()))
                        .newValue(String.valueOf(newEnd))
                        .build());
                segment.setEndTime(newEnd);
            }
        }

        if (request.getSpeakerId() != null) {
            Speaker speaker = speakerRepository.findById(request.getSpeakerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Speaker", "id", request.getSpeakerId()));
            if (!speaker.getJob().getId().equals(jobId)) {
                throw new BadRequestException("Speaker does not belong to this transcription job");
            }

            if (segment.getSpeakerId() == null || !segment.getSpeakerId().equals(request.getSpeakerId())) {
                revisionRepository.save(TranscriptRevision.builder()
                        .job(job)
                        .userId(userId)
                        .revisionType("REASSIGN_SPEAKER")
                        .segmentId(segmentId)
                        .fieldChanged("speakerId")
                        .oldValue(segment.getSpeakerId() != null ? segment.getSpeakerId().toString() : null)
                        .newValue(request.getSpeakerId().toString())
                        .build());
                segment.setSpeakerId(request.getSpeakerId());
            }
        }

        segment = segmentRepository.save(segment);

        Map<UUID, Speaker> speakerMap = speakerRepository.findByJobIdOrderBySpeakerLabelAsc(jobId)
                .stream()
                .collect(Collectors.toMap(Speaker::getId, Function.identity(), (a, b) -> a));

        return TranscriptSegmentResponse.fromEntity(segment, speakerMap);
    }

    @Transactional
    public TranscriptSegmentResponse reassignSpeaker(UUID userId, UUID jobId, UUID segmentId, ReassignSpeakerRequest request) {
        UpdateTranscriptSegmentRequest updateRequest = UpdateTranscriptSegmentRequest.builder()
                .speakerId(request.getSpeakerId())
                .build();
        return updateSegment(userId, jobId, segmentId, updateRequest);
    }

    @Transactional
    public TranscriptionJobResponse cancelJob(UUID userId, UUID jobId) {
        TranscriptionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("TranscriptionJob", "id", jobId));

        if (!job.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to cancel this job");
        }

        if (!"COMPLETED".equalsIgnoreCase(job.getStatus()) && !"FAILED".equalsIgnoreCase(job.getStatus())) {
            job.setStatus("CANCELLED");
            job.setCurrentStage("CANCELLED");
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);

            if (job.getVideo() != null && "PROCESSING".equalsIgnoreCase(job.getVideo().getStatus())) {
                job.getVideo().setStatus("READY");
                videoRepository.save(job.getVideo());
            }

            securityAuditService.logSecurityEvent(
                    "JOB_CANCELLED",
                    userId.toString(),
                    jobId.toString(),
                    null,
                    "SUCCESS",
                    "User cancelled transcription job"
            );

            log.info("Job {} cancelled by user {}", jobId, userId);

            // Broadcast cancellation event to SSE subscribers
            sseEmitterService.broadcastCompletion(jobId, "CANCELLED", "Transcription job was cancelled by user.");
        }

        return TranscriptionJobResponse.fromEntity(job);
    }
}
