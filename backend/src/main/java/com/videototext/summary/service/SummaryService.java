package com.videototext.summary.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videototext.common.exception.ConflictException;
import com.videototext.common.exception.ForbiddenException;
import com.videototext.common.exception.ResourceNotFoundException;
import com.videototext.speaker.entity.Speaker;
import com.videototext.speaker.repository.SpeakerRepository;
import com.videototext.summary.dto.ActionItemDto;
import com.videototext.summary.dto.ChapterDto;
import com.videototext.summary.dto.GenerateSummaryRequest;
import com.videototext.summary.dto.SummaryResponse;
import com.videototext.summary.dto.TopicDto;
import com.videototext.summary.entity.Summary;
import com.videototext.summary.repository.SummaryRepository;
import com.videototext.transcription.entity.TranscriptSegment;
import com.videototext.transcription.entity.TranscriptionJob;
import com.videototext.transcription.repository.TranscriptSegmentRepository;
import com.videototext.transcription.repository.TranscriptionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryService {

    private final SummaryRepository summaryRepository;
    private final TranscriptionJobRepository jobRepository;
    private final TranscriptSegmentRepository segmentRepository;
    private final SpeakerRepository speakerRepository;
    private final ObjectMapper objectMapper;

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are",
            "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but",
            "by", "can", "cannot", "could", "did", "do", "does", "doing", "down", "during", "each",
            "few", "for", "from", "further", "had", "has", "have", "having", "he", "her", "here",
            "hers", "herself", "him", "himself", "his", "how", "i", "if", "in", "into", "is", "it",
            "its", "itself", "let's", "me", "more", "most", "my", "myself", "no", "nor", "not", "of",
            "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves", "out",
            "over", "own", "same", "she", "should", "so", "some", "such", "than", "that", "the",
            "their", "theirs", "them", "themselves", "then", "there", "these", "they", "this",
            "those", "through", "to", "too", "under", "until", "up", "very", "was", "we", "were",
            "what", "when", "where", "which", "while", "who", "whom", "why", "with", "would",
            "you", "your", "yours", "yourself", "yourselves", "also", "just", "like", "okay", "yeah"
    ));

    @Transactional(readOnly = true)
    public SummaryResponse getSummary(UUID userId, UUID jobId) {
        TranscriptionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("TranscriptionJob", "id", jobId));

        if (!job.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to view summary for this job");
        }

        Summary summary = summaryRepository.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Summary", "jobId", jobId));

        return mapToResponse(summary);
    }

    @Transactional
    public SummaryResponse generateSummary(UUID userId, UUID jobId, GenerateSummaryRequest request) {
        TranscriptionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("TranscriptionJob", "id", jobId));

        if (!job.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to generate summary for this job");
        }

        if (!"COMPLETED".equalsIgnoreCase(job.getStatus())) {
            throw new ConflictException("Transcription job is not completed yet (current status: " + job.getStatus() + ")");
        }

        List<TranscriptSegment> segments = segmentRepository.findByJobIdOrderBySegmentIndexAsc(jobId);
        Map<UUID, Speaker> speakerMap = speakerRepository.findByJobIdOrderBySpeakerLabelAsc(jobId).stream()
                .collect(Collectors.toMap(Speaker::getId, s -> s));

        String length = request.getSummaryLength() != null ? request.getSummaryLength().toLowerCase().trim() : "medium";
        String format = request.getSummaryFormat() != null ? request.getSummaryFormat().toLowerCase().trim() : "paragraph";
        String provider = request.getProvider() != null ? request.getProvider() : "local";

        String summaryText = buildSummaryText(segments, length, format);

        List<String> keyPoints = Boolean.TRUE.equals(request.getExtractKeyPoints())
                ? buildKeyPoints(segments)
                : Collections.emptyList();

        List<ActionItemDto> actionItems = Boolean.TRUE.equals(request.getExtractActionItems())
                ? buildActionItems(segments, speakerMap)
                : Collections.emptyList();

        List<TopicDto> topics = Boolean.TRUE.equals(request.getExtractTopics())
                ? buildTopics(segments)
                : Collections.emptyList();

        List<ChapterDto> chapters = Boolean.TRUE.equals(request.getGenerateChapters())
                ? buildChapters(segments)
                : Collections.emptyList();

        Summary summary = summaryRepository.findByJobId(jobId)
                .orElse(Summary.builder().job(job).build());

        summary.setSummaryText(summaryText);
        summary.setKeyPointsJson(writeJson(keyPoints));
        summary.setActionItemsJson(writeJson(actionItems));
        summary.setTopicsJson(writeJson(topics));
        summary.setChaptersJson(writeJson(chapters));
        summary.setLlmProvider(provider);
        summary.setLlmModel("local-extractive-nlp-v1");

        summary = summaryRepository.save(summary);
        log.info("Successfully generated and saved summary for job: {}", jobId);

        return mapToResponse(summary);
    }

    @Transactional
    public void deleteSummary(UUID userId, UUID jobId) {
        TranscriptionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("TranscriptionJob", "id", jobId));

        if (!job.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to delete summary for this job");
        }

        summaryRepository.deleteByJobId(jobId);
        log.info("Deleted summary for job: {}", jobId);
    }

    // =========================================================================
    // Core NLP Extractive & Insights Logic
    // =========================================================================

    private String buildSummaryText(List<TranscriptSegment> segments, String length, String format) {
        if (segments == null || segments.isEmpty()) {
            return "No transcript segments available for summarization.";
        }

        List<String> sentences = new ArrayList<>();
        for (TranscriptSegment seg : segments) {
            if (seg.getText() != null && !seg.getText().isBlank()) {
                sentences.addAll(splitSentences(seg.getText()));
            }
        }

        if (sentences.isEmpty()) {
            return "Transcript contains no readable sentences.";
        }

        int targetCount;
        switch (length) {
            case "short" -> targetCount = Math.min(2, sentences.size());
            case "detailed" -> targetCount = Math.min(7, sentences.size());
            default -> targetCount = Math.min(4, sentences.size());
        }

        List<String> selected = extractTopSentences(sentences, targetCount);

        if ("bullets".equalsIgnoreCase(format)) {
            return selected.stream()
                    .map(s -> "• " + s.replaceAll("\\.+$", ""))
                    .collect(Collectors.joining("\n"));
        } else if ("executive".equalsIgnoreCase(format)) {
            String lead = selected.get(0);
            String rest = selected.size() > 1 ? String.join(" ", selected.subList(1, selected.size())) : lead;
            return "Executive Brief:\n" + lead + "\n\nKey Strategic Discussion:\n" + rest + "\n\nOutcome: Next steps and action points confirmed.";
        } else {
            if ("detailed".equalsIgnoreCase(length) && selected.size() >= 4) {
                int half = selected.size() / 2;
                return String.join(" ", selected.subList(0, half)) + "\n\n" + String.join(" ", selected.subList(half, selected.size()));
            }
            return String.join(" ", selected);
        }
    }

    private List<String> buildKeyPoints(List<TranscriptSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> sentences = new ArrayList<>();
        for (TranscriptSegment seg : segments) {
            if (seg.getText() != null && !seg.getText().isBlank()) {
                sentences.addAll(splitSentences(seg.getText()));
            }
        }

        List<String> top = extractTopSentences(sentences, Math.min(5, sentences.size()));
        return top.stream()
                .map(s -> s.endsWith(".") ? s : s + ".")
                .collect(Collectors.toList());
    }

    private List<ActionItemDto> buildActionItems(List<TranscriptSegment> segments, Map<UUID, Speaker> speakerMap) {
        List<ActionItemDto> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        Pattern pattern = Pattern.compile("(?i)\\b(?:we|i|you|team|he|she)\\s+(?:need to|should|will|must|have to|ought to|are assigned to|shall)\\s+([^.!?]+)");

        for (TranscriptSegment seg : segments) {
            if (seg.getText() == null) continue;
            Matcher matcher = pattern.matcher(seg.getText());
            while (matcher.find()) {
                String task = matcher.group(0).trim();
                if (task.length() > 120) {
                    task = task.substring(0, 117) + "...";
                }
                String norm = task.toLowerCase();
                if (!seen.contains(norm)) {
                    seen.add(norm);
                    String speakerName = resolveSpeaker(seg, speakerMap);
                    items.add(ActionItemDto.builder()
                            .task(task)
                            .assignee(speakerName)
                            .timestamp((double) seg.getStartTime())
                            .completed(false)
                            .build());
                }
            }
        }

        if (items.isEmpty() && !segments.isEmpty()) {
            TranscriptSegment first = segments.get(0);
            items.add(ActionItemDto.builder()
                    .task("Review finalized transcript and execute discussed action items")
                    .assignee(resolveSpeaker(first, speakerMap))
                    .timestamp((double) first.getStartTime())
                    .completed(false)
                    .build());
        }

        return items;
    }

    private List<TopicDto> buildTopics(List<TranscriptSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Integer> wordCounts = new HashMap<>();
        Map<String, Double> firstSeenTime = new HashMap<>();

        for (TranscriptSegment seg : segments) {
            if (seg.getText() == null) continue;
            String[] tokens = seg.getText().toLowerCase().replaceAll("[^a-zA-Z0-9 ]", "").split("\\s+");
            for (String tok : tokens) {
                if (tok.length() >= 4 && !STOPWORDS.contains(tok)) {
                    wordCounts.put(tok, wordCounts.getOrDefault(tok, 0) + 1);
                    firstSeenTime.putIfAbsent(tok, (double) seg.getStartTime());
                }
            }
        }

        if (wordCounts.isEmpty()) {
            return List.of(TopicDto.builder().name("General Discussion").relevance(1.0).startTime(0.0).build());
        }

        int maxCount = wordCounts.values().stream().max(Integer::compareTo).orElse(1);

        return wordCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    String capitalized = e.getKey().substring(0, 1).toUpperCase(Locale.ROOT) + e.getKey().substring(1);
                    double rel = Math.round((e.getValue() / (double) maxCount) * 100.0) / 100.0;
                    return TopicDto.builder()
                            .name(capitalized)
                            .relevance(rel)
                            .startTime(firstSeenTime.getOrDefault(e.getKey(), 0.0))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<ChapterDto> buildChapters(List<TranscriptSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return Collections.emptyList();
        }

        int n = segments.size();
        float start = segments.get(0).getStartTime();
        float end = segments.get(n - 1).getEndTime();

        if (n <= 3 || (end - start) < 30.0f) {
            return List.of(ChapterDto.builder()
                    .title("Overview & Discussion")
                    .startTime((double) start)
                    .endTime((double) end)
                    .summary(buildSummaryText(segments, "short", "paragraph"))
                    .build());
        }

        int numChapters = Math.min(3, Math.max(2, n / 3));
        int chunkSize = n / numChapters;
        List<ChapterDto> chapters = new ArrayList<>();

        for (int i = 0; i < numChapters; i++) {
            int startIdx = i * chunkSize;
            int endIdx = (i == numChapters - 1) ? n : (i + 1) * chunkSize;
            List<TranscriptSegment> subList = segments.subList(startIdx, endIdx);
            if (subList.isEmpty()) continue;

            double cStart = subList.get(0).getStartTime();
            double cEnd = subList.get(subList.size() - 1).getEndTime();

            String title;
            if (i == 0) {
                title = "Introduction & Overview";
            } else if (i == numChapters - 1) {
                title = "Wrap Up & Action Items";
            } else {
                title = "Main Discussion & Analysis";
            }

            chapters.add(ChapterDto.builder()
                    .title(title)
                    .startTime(cStart)
                    .endTime(cEnd)
                    .summary(buildSummaryText(subList, "short", "paragraph"))
                    .build());
        }

        return chapters;
    }

    // =========================================================================
    // Helper NLP Utilities
    // =========================================================================

    private List<String> splitSentences(String text) {
        String[] raw = text.split("(?<=[.!?])\\s+");
        return Arrays.stream(raw)
                .map(String::trim)
                .filter(s -> s.length() > 5)
                .collect(Collectors.toList());
    }

    private List<String> extractTopSentences(List<String> sentences, int limit) {
        Map<String, Integer> freq = new HashMap<>();
        for (String s : sentences) {
            String[] words = s.toLowerCase().replaceAll("[^a-zA-Z ]", "").split("\\s+");
            for (String w : words) {
                if (w.length() >= 3 && !STOPWORDS.contains(w)) {
                    freq.put(w, freq.getOrDefault(w, 0) + 1);
                }
            }
        }

        List<ScoredSentence> scored = new ArrayList<>();
        int total = sentences.size();
        for (int i = 0; i < total; i++) {
            String sentence = sentences.get(i);
            String[] words = sentence.toLowerCase().replaceAll("[^a-zA-Z ]", "").split("\\s+");
            double score = 0.0;
            for (String w : words) {
                score += freq.getOrDefault(w, 0);
            }
            if (i == 0) score *= 1.4; // Initial context boost
            if (i == total - 1) score *= 1.2; // Final conclusion boost
            scored.add(new ScoredSentence(i, sentence, score));
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredSentence::score).reversed())
                .limit(limit)
                .sorted(Comparator.comparingInt(ScoredSentence::index))
                .map(ScoredSentence::text)
                .collect(Collectors.toList());
    }

    private String resolveSpeaker(TranscriptSegment seg, Map<UUID, Speaker> speakerMap) {
        if (seg.getSpeakerId() != null && speakerMap.containsKey(seg.getSpeakerId())) {
            Speaker s = speakerMap.get(seg.getSpeakerId());
            if (s.getDisplayName() != null && !s.getDisplayName().isBlank()) {
                return s.getDisplayName();
            }
            if (s.getSpeakerLabel() != null && !s.getSpeakerLabel().isBlank()) {
                return s.getSpeakerLabel();
            }
        }
        return "Participant";
    }

    private SummaryResponse mapToResponse(Summary s) {
        return SummaryResponse.builder()
                .id(s.getId())
                .jobId(s.getJob().getId())
                .summaryText(s.getSummaryText())
                .summaryLength("medium")
                .summaryFormat("paragraph")
                .keyPoints(readJsonList(s.getKeyPointsJson(), new TypeReference<List<String>>() {}))
                .actionItems(readJsonList(s.getActionItemsJson(), new TypeReference<List<ActionItemDto>>() {}))
                .topics(readJsonList(s.getTopicsJson(), new TypeReference<List<TopicDto>>() {}))
                .chapters(readJsonList(s.getChaptersJson(), new TypeReference<List<ChapterDto>>() {}))
                .llmProvider(s.getLlmProvider())
                .llmModel(s.getLlmModel())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private <T> String writeJson(T object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            log.warn("Failed to serialize object to JSON: {}", e.getMessage());
            return "[]";
        }
    }

    private <T> List<T> readJsonList(String json, TypeReference<List<T>> typeRef) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.warn("Failed to parse JSON list: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private record ScoredSentence(int index, String text, double score) {}
}
