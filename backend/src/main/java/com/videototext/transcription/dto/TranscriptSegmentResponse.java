package com.videototext.transcription.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videototext.transcription.entity.TranscriptSegment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptSegmentResponse {

    private UUID id;
    private int segmentIndex;
    private float startTime;
    private float endTime;
    private String text;
    private float confidence;
    private String language;
    private String speakerLabel;
    private List<WordDto> words;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WordDto {
        private String word;
        private float startTime;
        private float endTime;
        private float confidence;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static TranscriptSegmentResponse fromEntity(TranscriptSegment seg) {
        List<WordDto> wordList = Collections.emptyList();
        if (seg.getWordTimestampsJson() != null && !seg.getWordTimestampsJson().isBlank()) {
            try {
                wordList = OBJECT_MAPPER.readValue(seg.getWordTimestampsJson(), new TypeReference<List<WordDto>>() {});
            } catch (Exception ignored) {
            }
        }

        return TranscriptSegmentResponse.builder()
                .id(seg.getId())
                .segmentIndex(seg.getSegmentIndex())
                .startTime(seg.getStartTime())
                .endTime(seg.getEndTime())
                .text(seg.getText())
                .confidence(seg.getConfidence())
                .language(seg.getLanguage())
                .speakerLabel(seg.getSpeakerId() != null ? "Speaker " + seg.getSpeakerId() : null)
                .words(wordList)
                .build();
    }
}
