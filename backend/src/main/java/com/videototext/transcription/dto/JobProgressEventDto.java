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
public class JobProgressEventDto {

    private UUID jobId;
    private String stage;
    private float progressPercent;
    private int completedChunks;
    private int totalChunks;
    private String message;
    private Instant timestamp;
}
