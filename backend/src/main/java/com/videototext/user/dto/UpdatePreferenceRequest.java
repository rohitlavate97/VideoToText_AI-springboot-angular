package com.videototext.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePreferenceRequest {

    @Size(min = 2, max = 10, message = "Language code must be between 2 and 10 characters")
    private String defaultLanguage;

    @Pattern(regexp = "^(local|cloud)$", message = "Processing mode must be either 'local' or 'cloud'")
    private String processingMode;

    @Size(max = 50, message = "ASR engine name cannot exceed 50 characters")
    private String preferredAsrEngine;

    @Size(max = 50, message = "ASR model name cannot exceed 50 characters")
    private String preferredAsrModel;
}
