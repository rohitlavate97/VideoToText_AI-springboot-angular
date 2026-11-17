package com.videototext.summary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionItemDto {
    private String task;
    private String assignee;
    private Double timestamp;
    private Boolean completed;
}
