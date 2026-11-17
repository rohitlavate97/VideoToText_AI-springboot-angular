package com.videototext.summary.dto;

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
public class SummaryResponse {
    private UUID id;
    private UUID jobId;
    private String summaryText;
    private String summaryLength;
    private String summaryFormat;
    private List<String> keyPoints;
    private List<ActionItemDto> actionItems;
    private List<TopicDto> topics;
    private List<ChapterDto> chapters;
    private String llmProvider;
    private String llmModel;
    private Instant createdAt;
    private Instant updatedAt;
}
