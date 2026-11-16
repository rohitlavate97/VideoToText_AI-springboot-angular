package com.videototext.translation.dto;

import com.videototext.translation.entity.Translation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranslationResponse {

    private UUID id;
    private UUID jobId;
    private String sourceLanguage;
    private String targetLanguage;
    private String translationEngine;
    private String status;
    private String storageKey;
    private List<TranslatedSegmentDto> segments;
    private String fullText;
    private Instant createdAt;

    public static TranslationResponse fromEntity(Translation t, List<TranslatedSegmentDto> segments) {
        String full = "";
        if (segments != null && !segments.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (TranslatedSegmentDto s : segments) {
                if (s.getTranslatedText() != null && !s.getTranslatedText().isBlank()) {
                    if (!sb.isEmpty()) sb.append(" ");
                    sb.append(s.getTranslatedText());
                }
            }
            full = sb.toString();
        }

        return TranslationResponse.builder()
                .id(t.getId())
                .jobId(t.getJob().getId())
                .sourceLanguage(t.getSourceLanguage())
                .targetLanguage(t.getTargetLanguage())
                .translationEngine(t.getTranslationEngine())
                .status(t.getStatus())
                .storageKey(t.getStorageKey())
                .segments(segments != null ? segments : Collections.emptyList())
                .fullText(full)
                .createdAt(t.getCreatedAt())
                .build();
    }
}
