package com.videototext.summary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateSummaryRequest {

    @Builder.Default
    private String summaryLength = "medium";

    @Builder.Default
    private String summaryFormat = "paragraph";

    @Builder.Default
    private Boolean extractKeyPoints = true;

    @Builder.Default
    private Boolean extractActionItems = true;

    @Builder.Default
    private Boolean extractTopics = true;

    @Builder.Default
    private Boolean generateChapters = true;

    private String provider;
}
