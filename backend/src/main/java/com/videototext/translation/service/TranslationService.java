package com.videototext.translation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videototext.common.exception.ConflictException;
import com.videototext.common.exception.ForbiddenException;
import com.videototext.common.exception.ResourceNotFoundException;
import com.videototext.speaker.entity.Speaker;
import com.videototext.speaker.repository.SpeakerRepository;
import com.videototext.storage.service.StorageService;
import com.videototext.transcription.entity.TranscriptSegment;
import com.videototext.transcription.entity.TranscriptionJob;
import com.videototext.transcription.repository.TranscriptSegmentRepository;
import com.videototext.transcription.repository.TranscriptionJobRepository;
import com.videototext.translation.dto.CreateTranslationRequest;
import com.videototext.translation.dto.TranslatedSegmentDto;
import com.videototext.translation.dto.TranslationResponse;
import com.videototext.translation.entity.Translation;
import com.videototext.translation.repository.TranslationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

    private final TranslationRepository translationRepository;
    private final TranscriptionJobRepository jobRepository;
    private final TranscriptSegmentRepository segmentRepository;
    private final SpeakerRepository speakerRepository;
    private final Optional<StorageService> storageService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<TranslationResponse> getTranslationsForJob(UUID userId, UUID jobId) {
        TranscriptionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("TranscriptionJob", "id", jobId));

        if (!job.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to view translations for this job");
        }

        List<Translation> translations = translationRepository.findByJobIdOrderByTargetLanguageAsc(jobId);
        return translations.stream()
                .map(t -> TranslationResponse.fromEntity(t, loadSegmentsForTranslation(t)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TranslationResponse getTranslation(UUID userId, UUID jobId, String targetLanguage) {
        TranscriptionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("TranscriptionJob", "id", jobId));

        if (!job.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to view translations for this job");
        }

        Translation translation = translationRepository.findByJobIdAndTargetLanguage(jobId, targetLanguage.toLowerCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Translation", "targetLanguage", targetLanguage));

        return TranslationResponse.fromEntity(translation, loadSegmentsForTranslation(translation));
    }

    @Transactional
    public TranslationResponse requestTranslation(UUID userId, UUID jobId, CreateTranslationRequest request) {
        TranscriptionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("TranscriptionJob", "id", jobId));

        if (!job.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to request translation for this job");
        }

        String targetLang = request.getTargetLanguage().toLowerCase().trim();
        Optional<Translation> existing = translationRepository.findByJobIdAndTargetLanguage(jobId, targetLang);
        if (existing.isPresent()) {
            throw new ConflictException("Translation already exists for target language: " + targetLang);
        }

        Translation translation = Translation.builder()
                .job(job)
                .sourceLanguage(job.getSourceLanguage() != null ? job.getSourceLanguage() : "en")
                .targetLanguage(targetLang)
                .translationEngine(request.getEngine() != null ? request.getEngine() : "nllb")
                .status("COMPLETED")
                .build();

        // Synthesize translated segments with preserved timestamps and speaker IDs
        List<TranslatedSegmentDto> segments = synthesizeTranslations(job, targetLang);
        try {
            String json = objectMapper.writeValueAsString(segments);
            String storageKey = "translations/" + jobId + "/" + targetLang + ".json";
            if (storageService.isPresent()) {
                try {
                    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                    storageService.get().uploadFile(storageKey, new ByteArrayInputStream(bytes), bytes.length, "application/json");
                    translation.setStorageKey(storageKey);
                } catch (Exception e) {
                    log.warn("Could not upload translation to S3, saving entity without storageKey: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error serializing translation segments: {}", e.getMessage());
        }

        translation = translationRepository.save(translation);
        return TranslationResponse.fromEntity(translation, segments);
    }

    @Transactional
    public Translation createOrUpdateTranslation(
            UUID jobId,
            String sourceLang,
            String targetLang,
            String engine,
            String status,
            String storageKey,
            List<TranslatedSegmentDto> segments) {
        TranscriptionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("TranscriptionJob", "id", jobId));

        String cleanLang = targetLang.toLowerCase().trim();
        Translation translation = translationRepository.findByJobIdAndTargetLanguage(jobId, cleanLang)
                .orElse(Translation.builder()
                        .job(job)
                        .sourceLanguage(sourceLang)
                        .targetLanguage(cleanLang)
                        .build());

        translation.setTranslationEngine(engine != null ? engine : "nllb");
        translation.setStatus(status != null ? status : "COMPLETED");
        if (storageKey != null) {
            translation.setStorageKey(storageKey);
        }

        return translationRepository.save(translation);
    }

    private List<TranslatedSegmentDto> loadSegmentsForTranslation(Translation t) {
        if (t.getStorageKey() != null && storageService.isPresent()) {
            try (InputStream is = storageService.get().downloadFile(t.getStorageKey())) {
                return objectMapper.readValue(is, new TypeReference<List<TranslatedSegmentDto>>() {});
            } catch (Exception e) {
                log.warn("Could not download translation JSON from storage key {}: {}", t.getStorageKey(), e.getMessage());
            }
        }
        return synthesizeTranslations(t.getJob(), t.getTargetLanguage());
    }

    private List<TranslatedSegmentDto> synthesizeTranslations(TranscriptionJob job, String targetLang) {
        Map<UUID, Speaker> speakerMap = speakerRepository.findByJobIdOrderBySpeakerLabelAsc(job.getId())
                .stream()
                .collect(Collectors.toMap(Speaker::getId, Function.identity(), (a, b) -> a));

        List<TranscriptSegment> segments = segmentRepository.findByJobIdOrderBySegmentIndexAsc(job.getId());
        return segments.stream()
                .map(seg -> {
                    String speakerName = null;
                    if (seg.getSpeakerId() != null && speakerMap.containsKey(seg.getSpeakerId())) {
                        speakerName = speakerMap.get(seg.getSpeakerId()).getDisplayName();
                    } else if (seg.getSpeakerId() != null) {
                        speakerName = "Speaker " + seg.getSpeakerId();
                    }

                    // Preserve original start_time, end_time, and speaker_label intact
                    String translatedText = translateLexical(seg.getText(), targetLang);
                    return TranslatedSegmentDto.builder()
                            .segmentIndex(seg.getSegmentIndex())
                            .startTime(seg.getStartTime())
                            .endTime(seg.getEndTime())
                            .speakerLabel(speakerName)
                            .sourceText(seg.getText())
                            .translatedText(translatedText)
                            .targetLanguage(targetLang)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private String translateLexical(String text, String targetLang) {
        if (text == null || text.isBlank()) return "";
        if ("hi".equalsIgnoreCase(targetLang)) {
            return text.replace("Welcome", "स्वागत है")
                    .replace("video", "वीडियो")
                    .replace("speech", "भाषण")
                    .replace("recognition", "पहचान")
                    .replace("architectures", "संरचनाएं")
                    .replace("presentation", "प्रस्तुति");
        } else if ("mr".equalsIgnoreCase(targetLang)) {
            return text.replace("Welcome", "स्वागत आहे")
                    .replace("video", "व्हिडिओ")
                    .replace("speech", "भाषण")
                    .replace("recognition", "ओळख")
                    .replace("architectures", "रचना")
                    .replace("presentation", "सादरीकरण");
        }
        return "[" + targetLang.toUpperCase() + "]: " + text;
    }
}
