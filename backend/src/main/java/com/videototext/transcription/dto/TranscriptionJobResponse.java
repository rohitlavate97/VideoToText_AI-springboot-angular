package com.videototext.transcription.dto;

import com.videototext.transcription.entity.TranscriptionJob;
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
public class TranscriptionJobResponse {

    private UUID id;
    private UUID videoId;
    private String status;
    private String engine;
    private String model;
    private String language;
    private String currentStage;
    private float progressPercent;
    private int totalChunks;
    private int completedChunks;
    private String errorMessage;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Long processingDurationMs;

    public static TranscriptionJobResponse fromEntity(TranscriptionJob job) {
        return TranscriptionJobResponse.builder()
                .id(job.getId())
                .videoId(job.getVideo() != null ? job.getVideo().getId() : null)
                .status(job.getStatus())
                .engine(job.getAsrEngine())
                .model(job.getAsrModel())
                .language(job.getSourceLanguage())
                .currentStage(job.getCurrentStage())
                .progressPercent(job.getProgressPercent())
                .totalChunks(job.getTotalChunks())
                .completedChunks(job.getCompletedChunks())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .processingDurationMs(job.getProcessingDurationMs())
                .build();
    }
}
