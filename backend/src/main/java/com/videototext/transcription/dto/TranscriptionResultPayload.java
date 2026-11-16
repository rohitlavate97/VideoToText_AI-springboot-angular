package com.videototext.transcription.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class TranscriptionResultPayload {

    private UUID jobId;
    private UUID videoId;
    private String status;
    private Double durationSeconds;
    private String detectedLanguage;
    private int segmentsCount;
    private int speakersCount;
    private String errorMessage;
    private Instant completedAt;
}
