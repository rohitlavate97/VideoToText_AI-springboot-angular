package com.videototext.subtitle.dto;

import com.videototext.subtitle.entity.Subtitle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubtitleResponse {

    private UUID id;
    private UUID jobId;
    private String format;
    private String language;
    private String storageKey;
    private boolean includesSpeakers;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;

    public static SubtitleResponse fromEntity(Subtitle entity, String content) {
        if (entity == null) {
            return null;
        }
        return SubtitleResponse.builder()
                .id(entity.getId())
                .jobId(entity.getJob().getId())
                .format(entity.getFormat())
                .language(entity.getLanguage())
                .storageKey(entity.getStorageKey())
                .includesSpeakers(entity.isIncludesSpeakers())
                .content(content)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
