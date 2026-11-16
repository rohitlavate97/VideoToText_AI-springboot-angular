package com.videototext.video.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateUploadRequest {

    @NotBlank(message = "Filename is required")
    private String filename;

    @Min(value = 1, message = "File size must be greater than zero")
    private long fileSizeBytes;

    private String contentType;

    @Min(value = 1, message = "Total parts must be at least 1")
    private int totalParts;
}
