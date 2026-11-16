package com.videototext.transcription.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TranscriptionMessagePayload {

    private UUID jobId;
    private UUID videoId;
    private UUID userId;
    private String s3Key;
    private String language;
    private String task;
    private String engine;
    private String model;
    private String computeType;
    private boolean enableDiarization;
    private Integer maxSpeakers;
    private List<String> targetLanguages;
    private boolean generateSummary;
    private Instant createdAt;
}
