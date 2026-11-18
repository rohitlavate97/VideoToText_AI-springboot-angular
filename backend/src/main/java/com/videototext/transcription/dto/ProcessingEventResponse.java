package com.videototext.transcription.dto;

import com.videototext.transcription.entity.ProcessingEventRecord;
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
public class ProcessingEventResponse {

    private UUID id;
    private UUID jobId;
    private String eventType;
    private String stage;
    private float progressPercent;
    private String message;
    private String metadataJson;
    private Instant createdAt;

    public static ProcessingEventResponse fromEntity(ProcessingEventRecord record) {
        return ProcessingEventResponse.builder()
                .id(record.getId())
                .jobId(record.getJob() != null ? record.getJob().getId() : null)
                .eventType(record.getEventType())
                .stage(record.getStage())
                .progressPercent(record.getProgressPercent())
                .message(record.getMessage())
                .metadataJson(record.getMetadataJson())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
