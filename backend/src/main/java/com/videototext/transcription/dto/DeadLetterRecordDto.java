package com.videototext.transcription.dto;

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
public class DeadLetterRecordDto {
    private UUID jobId;
    private String originalRoutingKey;
    private String reason;
    private String payloadSnippet;
    private Instant receivedAt;
}
