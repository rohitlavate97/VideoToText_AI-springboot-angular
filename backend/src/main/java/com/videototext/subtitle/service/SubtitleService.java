package com.videototext.subtitle.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videototext.common.exception.BadRequestException;
import com.videototext.common.exception.ForbiddenException;
import com.videototext.common.exception.ResourceNotFoundException;
import com.videototext.export.dto.ExportResponse;
import com.videototext.export.entity.Export;
import com.videototext.export.repository.ExportRepository;
import com.videototext.speaker.entity.Speaker;
import com.videototext.speaker.repository.SpeakerRepository;
import com.videototext.storage.service.StorageService;
import com.videototext.subtitle.dto.GenerateSubtitleRequest;
import com.videototext.subtitle.dto.SubtitleResponse;
import com.videototext.subtitle.entity.Subtitle;
import com.videototext.subtitle.repository.SubtitleRepository;
import com.videototext.transcription.entity.TranscriptSegment;
import com.videototext.transcription.entity.TranscriptionJob;
import com.videototext.transcription.repository.TranscriptSegmentRepository;
import com.videototext.transcription.repository.TranscriptionJobRepository;
import com.videototext.translation.dto.TranslatedSegmentDto;
import com.videototext.translation.entity.Translation;
import com.videototext.translation.repository.TranslationRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubtitleService {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("SRT", "VTT", "TXT", "JSON");

    private final SubtitleRepository subtitleRepository;
    private final ExportRepository exportRepository;
    private final TranscriptionJobRepository jobRepository;
    private final TranscriptSegmentRepository segmentRepository;
    private final SpeakerRepository speakerRepository;
    private final TranslationRepository translationRepository;
    private final Optional<StorageService> storageService;
    private final ObjectMapper objectMapper;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubtitleCue {
        private int id;
        private float startTime;
        private float endTime;
        private String text;
        private String speaker;
    }

    @Transactional(readOnly = true)
    public List<SubtitleResponse> getSubtitlesForJob(UUID userId, UUID jobId) {
        TranscriptionJob job = validateJobOwnership(userId, jobId);
        List<Subtitle> subtitles = subtitleRepository.findByJobIdOrderByCreatedAtDesc(job.getId());
        return subtitles.stream()
                .map(s -> SubtitleResponse.fromEntity(s, null))
                .collect(Collectors.toList());
    }

    @Transactional
    public SubtitleResponse generateSubtitle(UUID userId, UUID jobId, GenerateSubtitleRequest request) {
        TranscriptionJob job = validateJobOwnership(userId, jobId);

        String format = normalizeFormat(request.getFormat());
        String language = normalizeLanguage(request.getLanguage(), job.getSourceLanguage());
        boolean includeSpeakers = Boolean.TRUE.equals(request.getIncludeSpeakers());

        List<SubtitleCue> cues = loadAndNormalizeCues(job, language, includeSpeakers);
        String content = formatCues(cues, format, language, includeSpeakers);
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        String storageKey = String.format("subtitles/%s/%s%s.%s",
                jobId,
                language,
                includeSpeakers ? "_speakers" : "",
                format.toLowerCase(Locale.ROOT));

        // Save to S3 if available
        if (storageService.isPresent()) {
            try {
                String mimeType = getMimeType(format);
                storageService.get().uploadFile(storageKey, new ByteArrayInputStream(contentBytes), contentBytes.length, mimeType);
            } catch (Exception e) {
                log.warn("Could not upload subtitle to S3, saving entity without storageKey: {}", e.getMessage());
            }
        }

        // Upsert subtitle entity
        Subtitle subtitle = subtitleRepository
                .findByJobIdAndFormatAndLanguageAndIncludesSpeakers(job.getId(), format, language, includeSpeakers)
                .orElse(Subtitle.builder()
                        .job(job)
                        .format(format)
                        .language(language)
                        .includesSpeakers(includeSpeakers)
                        .build());

        subtitle.setStorageKey(storageKey);
        subtitle = subtitleRepository.save(subtitle);

        // Record in export audit log
        try {
            Export export = Export.builder()
                    .job(job)
                    .user(job.getUser())
                    .exportType("SUBTITLE")
                    .format(format)
                    .language(language)
                    .storageKey(storageKey)
                    .fileSizeBytes(contentBytes.length)
                    .build();
            exportRepository.save(export);
        } catch (Exception e) {
            log.warn("Could not record export record: {}", e.getMessage());
        }

        return SubtitleResponse.fromEntity(subtitle, content);
    }

    @Transactional(readOnly = true)
    public byte[] downloadSubtitle(UUID userId, UUID jobId, String rawFormat, String rawLanguage, boolean includeSpeakers) {
        TranscriptionJob job = validateJobOwnership(userId, jobId);
        String format = normalizeFormat(rawFormat);
        String language = normalizeLanguage(rawLanguage, job.getSourceLanguage());

        Optional<Subtitle> existing = subtitleRepository
                .findByJobIdAndFormatAndLanguageAndIncludesSpeakers(job.getId(), format, language, includeSpeakers);

        if (existing.isPresent() && storageService.isPresent() && existing.get().getStorageKey() != null) {
            try (InputStream is = storageService.get().downloadFile(existing.get().getStorageKey())) {
                return is.readAllBytes();
            } catch (Exception e) {
                log.warn("Failed to download subtitle from storage key {}: {}", existing.get().getStorageKey(), e.getMessage());
            }
        }

        // Fallback: dynamically generate on-the-fly
        List<SubtitleCue> cues = loadAndNormalizeCues(job, language, includeSpeakers);
        String content = formatCues(cues, format, language, includeSpeakers);
        return content.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] downloadSubtitleById(UUID userId, UUID jobId, UUID subtitleId) {
        TranscriptionJob job = validateJobOwnership(userId, jobId);
        Subtitle subtitle = subtitleRepository.findById(subtitleId)
                .orElseThrow(() -> new ResourceNotFoundException("Subtitle", "id", subtitleId));

        if (!subtitle.getJob().getId().equals(job.getId())) {
            throw new BadRequestException("Subtitle does not belong to transcription job: " + jobId);
        }

        if (storageService.isPresent() && subtitle.getStorageKey() != null) {
            try (InputStream is = storageService.get().downloadFile(subtitle.getStorageKey())) {
                return is.readAllBytes();
            } catch (Exception e) {
                log.warn("Failed to download subtitle from storage key {}: {}", subtitle.getStorageKey(), e.getMessage());
            }
        }

        List<SubtitleCue> cues = loadAndNormalizeCues(job, subtitle.getLanguage(), subtitle.isIncludesSpeakers());
        String content = formatCues(cues, subtitle.getFormat(), subtitle.getLanguage(), subtitle.isIncludesSpeakers());
        return content.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public void deleteSubtitle(UUID userId, UUID jobId, UUID subtitleId) {
        TranscriptionJob job = validateJobOwnership(userId, jobId);
        Subtitle subtitle = subtitleRepository.findById(subtitleId)
                .orElseThrow(() -> new ResourceNotFoundException("Subtitle", "id", subtitleId));

        if (!subtitle.getJob().getId().equals(job.getId())) {
            throw new BadRequestException("Subtitle does not belong to transcription job: " + jobId);
        }

        if (storageService.isPresent() && subtitle.getStorageKey() != null) {
            try {
                storageService.get().deleteFile(subtitle.getStorageKey());
            } catch (Exception e) {
                log.warn("Could not delete subtitle from storage: {}", e.getMessage());
            }
        }

        subtitleRepository.delete(subtitle);
    }

    @Transactional
    public byte[] exportTranscript(UUID userId, UUID jobId, String rawFormat, String rawLanguage) {
        TranscriptionJob job = validateJobOwnership(userId, jobId);
        String format = normalizeFormat(rawFormat);
        String language = normalizeLanguage(rawLanguage, job.getSourceLanguage());

        List<SubtitleCue> cues = loadAndNormalizeCues(job, language, true);
        String content = formatCues(cues, format, language, true);
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        String storageKey = String.format("exports/%s/%s.%s", jobId, language, format.toLowerCase(Locale.ROOT));

        if (storageService.isPresent()) {
            try {
                storageService.get().uploadFile(storageKey, new ByteArrayInputStream(contentBytes), contentBytes.length, getMimeType(format));
            } catch (Exception e) {
                log.warn("Could not upload export to storage: {}", e.getMessage());
            }
        }

        try {
            Export export = Export.builder()
                    .job(job)
                    .user(job.getUser())
                    .exportType("EXPORT")
                    .format(format)
                    .language(language)
                    .storageKey(storageKey)
                    .fileSizeBytes(contentBytes.length)
                    .build();
            exportRepository.save(export);
        } catch (Exception e) {
            log.warn("Could not record export: {}", e.getMessage());
        }

        return contentBytes;
    }

    @Transactional(readOnly = true)
    public List<ExportResponse> getExportsForJob(UUID userId, UUID jobId) {
        TranscriptionJob job = validateJobOwnership(userId, jobId);
        return exportRepository.findByJobIdOrderByCreatedAtDesc(job.getId()).stream()
                .map(ExportResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Helper & Formatting Engine
    // -------------------------------------------------------------------------

    private TranscriptionJob validateJobOwnership(UUID userId, UUID jobId) {
        TranscriptionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("TranscriptionJob", "id", jobId));

        if (!job.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to access subtitles for this job");
        }
        return job;
    }

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return "SRT";
        }
        String upper = format.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_FORMATS.contains(upper)) {
            throw new BadRequestException("Unsupported subtitle format: " + format + ". Supported: " + SUPPORTED_FORMATS);
        }
        return upper;
    }

    private String normalizeLanguage(String lang, String jobSourceLang) {
        if (lang != null && !lang.isBlank()) {
            return lang.trim().toLowerCase(Locale.ROOT);
        }
        if (jobSourceLang != null && !jobSourceLang.isBlank()) {
            return jobSourceLang.trim().toLowerCase(Locale.ROOT);
        }
        return "en";
    }

    public String getMimeType(String format) {
        return switch (format.toUpperCase(Locale.ROOT)) {
            case "SRT" -> "application/x-subrip";
            case "VTT" -> "text/vtt";
            case "TXT" -> "text/plain";
            case "JSON" -> "application/json";
            default -> "text/plain";
        };
    }

    public String getFileExtension(String format) {
        return switch (format.toUpperCase(Locale.ROOT)) {
            case "SRT" -> ".srt";
            case "VTT" -> ".vtt";
            case "TXT" -> ".txt";
            case "JSON" -> ".json";
            default -> ".txt";
        };
    }

    private List<SubtitleCue> loadAndNormalizeCues(TranscriptionJob job, String targetLanguage, boolean includeSpeakers) {
        UUID jobId = job.getId();
        String sourceLanguage = job.getSourceLanguage() != null ? job.getSourceLanguage().toLowerCase(Locale.ROOT) : "en";

        // Load speakers map
        Map<UUID, String> speakerIdMap = new LinkedHashMap<>();
        Map<String, String> speakerLabelMap = new LinkedHashMap<>();
        List<Speaker> speakers = speakerRepository.findByJobIdOrderBySpeakerLabelAsc(jobId);
        for (Speaker s : speakers) {
            String name = (s.getDisplayName() != null && !s.getDisplayName().isBlank()) ? s.getDisplayName() : s.getSpeakerLabel();
            speakerIdMap.put(s.getId(), name);
            speakerLabelMap.put(s.getSpeakerLabel(), name);
        }

        List<SubtitleCue> rawCues = new ArrayList<>();

        if (targetLanguage.equalsIgnoreCase(sourceLanguage)) {
            // Source language segments
            List<TranscriptSegment> segments = segmentRepository.findByJobIdOrderBySegmentIndexAsc(jobId);
            for (TranscriptSegment seg : segments) {
                String speakerName = null;
                if (seg.getSpeakerId() != null) {
                    speakerName = speakerIdMap.get(seg.getSpeakerId());
                }
                rawCues.add(SubtitleCue.builder()
                        .id(seg.getSegmentIndex() + 1)
                        .startTime(seg.getStartTime())
                        .endTime(seg.getEndTime())
                        .text(seg.getText())
                        .speaker(speakerName)
                        .build());
            }
        } else {
            // Translated language
            Optional<Translation> optTranslation = translationRepository.findByJobIdAndTargetLanguage(jobId, targetLanguage);
            if (optTranslation.isPresent()) {
                Translation translation = optTranslation.get();
                List<TranslatedSegmentDto> translatedSegments = loadTranslatedSegments(translation);
                for (TranslatedSegmentDto tSeg : translatedSegments) {
                    String speakerName = tSeg.getSpeakerLabel();
                    if (speakerName != null && speakerLabelMap.containsKey(speakerName)) {
                        speakerName = speakerLabelMap.get(speakerName);
                    }
                    rawCues.add(SubtitleCue.builder()
                            .id(tSeg.getSegmentIndex() + 1)
                            .startTime(tSeg.getStartTime())
                            .endTime(tSeg.getEndTime())
                            .text(tSeg.getTranslatedText())
                            .speaker(speakerName)
                            .build());
                }
            } else {
                // Fallback to original transcript if translation not found
                List<TranscriptSegment> segments = segmentRepository.findByJobIdOrderBySegmentIndexAsc(jobId);
                for (TranscriptSegment seg : segments) {
                    String speakerName = seg.getSpeakerId() != null ? speakerIdMap.get(seg.getSpeakerId()) : null;
                    rawCues.add(SubtitleCue.builder()
                            .id(seg.getSegmentIndex() + 1)
                            .startTime(seg.getStartTime())
                            .endTime(seg.getEndTime())
                            .text(seg.getText())
                            .speaker(speakerName)
                            .build());
                }
            }
        }

        // Validate, sort, and normalize timestamps
        return normalizeCues(rawCues);
    }

    private List<TranslatedSegmentDto> loadTranslatedSegments(Translation t) {
        if (t.getStorageKey() != null && storageService.isPresent()) {
            try (InputStream is = storageService.get().downloadFile(t.getStorageKey())) {
                return objectMapper.readValue(is, new TypeReference<List<TranslatedSegmentDto>>() {});
            } catch (Exception e) {
                log.warn("Could not read translated segments from storage: {}", e.getMessage());
            }
        }
        return List.of();
    }

    private List<SubtitleCue> normalizeCues(List<SubtitleCue> cues) {
        if (cues == null || cues.isEmpty()) {
            return List.of();
        }

        List<SubtitleCue> valid = cues.stream()
                .filter(c -> c.getText() != null && !c.getText().trim().isBlank())
                .sorted(Comparator.comparingDouble(SubtitleCue::getStartTime).thenComparingDouble(SubtitleCue::getEndTime))
                .collect(Collectors.toList());

        List<SubtitleCue> normalized = new ArrayList<>();
        int index = 1;
        for (SubtitleCue c : valid) {
            float start = Math.max(0.0f, c.getStartTime());
            float end = c.getEndTime();
            if (end <= start) {
                end = start + 0.1f;
            }
            normalized.add(SubtitleCue.builder()
                    .id(index++)
                    .startTime(start)
                    .endTime(end)
                    .text(c.getText().trim())
                    .speaker(c.getSpeaker())
                    .build());
        }
        return normalized;
    }

    private String formatCues(List<SubtitleCue> cues, String format, String language, boolean includeSpeakers) {
        return switch (format.toUpperCase(Locale.ROOT)) {
            case "SRT" -> formatSrt(cues, includeSpeakers);
            case "VTT" -> formatVtt(cues, includeSpeakers);
            case "TXT" -> formatTxt(cues, includeSpeakers);
            case "JSON" -> formatJson(cues, language, includeSpeakers);
            default -> formatSrt(cues, includeSpeakers);
        };
    }

    private String formatSrt(List<SubtitleCue> cues, boolean includeSpeakers) {
        if (cues.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (SubtitleCue cue : cues) {
            sb.append(cue.getId()).append("\n");
            sb.append(formatSrtTimestamp(cue.getStartTime()))
                    .append(" --> ")
                    .append(formatSrtTimestamp(cue.getEndTime()))
                    .append("\n");

            if (includeSpeakers && cue.getSpeaker() != null && !cue.getSpeaker().isBlank()) {
                sb.append("[").append(cue.getSpeaker()).append("]: ");
            }
            sb.append(cue.getText()).append("\n\n");
        }
        return sb.toString().trim() + "\n";
    }

    private String formatVtt(List<SubtitleCue> cues, boolean includeSpeakers) {
        StringBuilder sb = new StringBuilder("WEBVTT\n\n");
        for (SubtitleCue cue : cues) {
            sb.append(cue.getId()).append("\n");
            sb.append(formatVttTimestamp(cue.getStartTime()))
                    .append(" --> ")
                    .append(formatVttTimestamp(cue.getEndTime()))
                    .append("\n");

            if (includeSpeakers && cue.getSpeaker() != null && !cue.getSpeaker().isBlank()) {
                sb.append("<v ").append(cue.getSpeaker()).append(">").append(cue.getText());
            } else {
                sb.append(cue.getText());
            }
            sb.append("\n\n");
        }
        return sb.toString().trim() + "\n";
    }

    private String formatTxt(List<SubtitleCue> cues, boolean includeSpeakers) {
        if (cues.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (SubtitleCue cue : cues) {
            sb.append(formatTxtTimestamp(cue.getStartTime())).append(" ");
            if (includeSpeakers && cue.getSpeaker() != null && !cue.getSpeaker().isBlank()) {
                sb.append("[").append(cue.getSpeaker()).append("]: ");
            }
            sb.append(cue.getText()).append("\n");
        }
        return sb.toString();
    }

    private String formatJson(List<SubtitleCue> cues, String language, boolean includeSpeakers) {
        try {
            float duration = cues.isEmpty() ? 0.0f : cues.get(cues.size() - 1).getEndTime();
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("format", "JSON");
            root.put("language", language);
            root.put("totalCues", cues.size());
            root.put("duration", duration);

            List<Map<String, Object>> cueList = new ArrayList<>();
            for (SubtitleCue cue : cues) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("id", cue.getId());
                c.put("startTime", cue.getStartTime());
                c.put("endTime", cue.getEndTime());
                c.put("duration", Math.round((cue.getEndTime() - cue.getStartTime()) * 1000.0f) / 1000.0f);
                if (includeSpeakers && cue.getSpeaker() != null) {
                    c.put("speaker", cue.getSpeaker());
                }
                c.put("text", cue.getText());
                cueList.add(c);
            }
            root.put("cues", cueList);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed to serialize subtitle cues to JSON: {}", e.getMessage());
            return "{\"format\":\"JSON\",\"cues\":[]}";
        }
    }

    public static String formatSrtTimestamp(float seconds) {
        long totalMillis = Math.round(Math.max(0.0f, seconds) * 1000.0);
        long hours = totalMillis / 3600000;
        long remainder = totalMillis % 3600000;
        long minutes = remainder / 60000;
        remainder = remainder % 60000;
        long secs = remainder / 1000;
        long millis = remainder % 1000;
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, secs, millis);
    }

    public static String formatVttTimestamp(float seconds) {
        long totalMillis = Math.round(Math.max(0.0f, seconds) * 1000.0);
        long hours = totalMillis / 3600000;
        long remainder = totalMillis % 3600000;
        long minutes = remainder / 60000;
        remainder = remainder % 60000;
        long secs = remainder / 1000;
        long millis = remainder % 1000;
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, secs, millis);
    }

    public static String formatTxtTimestamp(float seconds) {
        long totalMillis = Math.round(Math.max(0.0f, seconds) * 1000.0);
        long hours = totalMillis / 3600000;
        long minutes = (totalMillis % 3600000) / 60000;
        long secs = (totalMillis % 60000) / 1000;
        return String.format("[%02d:%02d:%02d]", hours, minutes, secs);
    }
}
