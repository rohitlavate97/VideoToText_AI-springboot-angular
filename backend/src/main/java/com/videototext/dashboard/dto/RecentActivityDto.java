package com.videototext.dashboard.dto;

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
public class RecentActivityDto {
    private UUID videoId;
    private UUID jobId;
    private String filename;
    private String status;
    private double durationSeconds;
    private String language;
    private Instant createdAt;
}
