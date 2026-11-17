package com.videototext.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {

    @Builder.Default
    private long totalVideos = 0;

    @Builder.Default
    private double totalAudioDurationSeconds = 0.0;

    @Builder.Default
    private long totalTranscribedWords = 0;

    @Builder.Default
    private long totalExports = 0;

    @Builder.Default
    private Map<String, Long> statusCounts = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, Long> languageDistribution = new LinkedHashMap<>();

    @Builder.Default
    private List<RecentActivityDto> recentActivity = new ArrayList<>();
}
