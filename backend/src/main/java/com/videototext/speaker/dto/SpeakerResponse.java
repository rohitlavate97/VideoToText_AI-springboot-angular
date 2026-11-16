package com.videototext.speaker.dto;

import com.videototext.speaker.entity.Speaker;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerResponse {

    private UUID id;
    private UUID jobId;
    private String speakerLabel;
    private String displayName;
    private int totalSpeakingTimeMs;
    private int segmentCount;

    public static SpeakerResponse fromEntity(Speaker speaker) {
        return SpeakerResponse.builder()
                .id(speaker.getId())
                .jobId(speaker.getJob() != null ? speaker.getJob().getId() : null)
                .speakerLabel(speaker.getSpeakerLabel())
                .displayName(speaker.getDisplayName() != null ? speaker.getDisplayName() : speaker.getSpeakerLabel())
                .totalSpeakingTimeMs(speaker.getTotalSpeakingTimeMs())
                .segmentCount(speaker.getSegmentCount())
                .build();
    }
}
