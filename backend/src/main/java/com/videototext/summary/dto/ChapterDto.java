package com.videototext.summary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterDto {
    private String title;
    private Double startTime;
    private Double endTime;
    private String summary;
}
