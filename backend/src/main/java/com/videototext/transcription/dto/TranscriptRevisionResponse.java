package com.videototext.transcription.dto;

import com.videototext.transcription.entity.TranscriptRevision;
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
public class TranscriptRevisionResponse {

    private UUID id;
    private UUID jobId;
    private UUID segmentId;
    private UUID userId;
    private String revisionType;
    private String fieldChanged;
    private String oldValue;
    private String newValue;
    private Instant createdAt;

    public static TranscriptRevisionResponse fromEntity(TranscriptRevision revision) {
        return TranscriptRevisionResponse.builder()
                .id(revision.getId())
                .jobId(revision.getJob().getId())
                .segmentId(revision.getSegmentId())
                .userId(revision.getUserId())
                .revisionType(revision.getRevisionType())
                .fieldChanged(revision.getFieldChanged())
                .oldValue(revision.getOldValue())
                .newValue(revision.getNewValue())
                .createdAt(revision.getCreatedAt())
                .build();
    }
}
