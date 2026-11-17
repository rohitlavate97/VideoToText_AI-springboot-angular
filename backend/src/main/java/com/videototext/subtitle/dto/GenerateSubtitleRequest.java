package com.videototext.subtitle.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateSubtitleRequest {

    @NotBlank(message = "Format is required (SRT, VTT, TXT, JSON)")
    @Schema(description = "Export format", example = "SRT")
    private String format;

    @Builder.Default
    @Schema(description = "Language code (source or translated)", example = "en")
    private String language = "en";

    @Builder.Default
    @Schema(description = "Whether to include speaker display names", example = "false")
    private Boolean includeSpeakers = false;
}
