package com.videototext.transcription.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTranscriptSegmentRequest {

    @Size(min = 1, max = 10000, message = "Transcript text cannot be empty or exceed 10000 characters")
    private String text;

    @DecimalMin(value = "0.0", message = "Start time must be non-negative")
    private Float startTime;

    @DecimalMin(value = "0.0", message = "End time must be non-negative")
    private Float endTime;

    private UUID speakerId;
}
