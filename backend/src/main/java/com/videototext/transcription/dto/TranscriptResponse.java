package com.videototext.transcription.dto;

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
public class TranscriptResponse {

    private UUID jobId;
    private UUID videoId;
    private String videoTitle;
    private String status;
    private String language;
    private float durationSeconds;
    private int totalSegments;
    private int totalWords;
    private String fullText;
    private List<TranscriptSegmentResponse> segments;
    private Instant completedAt;
}
