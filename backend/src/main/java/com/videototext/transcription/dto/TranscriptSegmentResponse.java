package com.videototext.transcription.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videototext.speaker.entity.Speaker;
import com.videototext.transcription.entity.TranscriptSegment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private UUID speakerId;
    private String speakerLabel;
    private String speakerDisplayName;
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
        return fromEntity(seg, null);
    }

    public static TranscriptSegmentResponse fromEntity(TranscriptSegment seg, Map<UUID, Speaker> speakerMap) {
        List<WordDto> wordList = Collections.emptyList();
        if (seg.getWordTimestampsJson() != null && !seg.getWordTimestampsJson().isBlank()) {
            try {
                wordList = OBJECT_MAPPER.readValue(seg.getWordTimestampsJson(), new TypeReference<List<WordDto>>() {});
            } catch (Exception ignored) {
            }
        }

        String label = null;
        String displayName = null;
        if (seg.getSpeakerId() != null) {
            if (speakerMap != null && speakerMap.containsKey(seg.getSpeakerId())) {
                Speaker sp = speakerMap.get(seg.getSpeakerId());
                label = sp.getSpeakerLabel();
                displayName = sp.getDisplayName();
            } else {
                label = "Speaker " + seg.getSpeakerId();
                displayName = label;
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
                .speakerId(seg.getSpeakerId())
                .speakerLabel(label)
                .speakerDisplayName(displayName)
                .words(wordList)
                .build();
    }
}
