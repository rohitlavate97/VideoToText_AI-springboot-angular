package com.videototext.transcription.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTranscriptionRequest {

    @NotNull(message = "videoId is required")
    private UUID videoId;

    @Builder.Default
    private String language = "auto";

    @Builder.Default
    private String engine = "faster-whisper";

    @Builder.Default
    private String model = "small";

    @Builder.Default
    private String computeType = "int8_float16";

    @Builder.Default
    private boolean enableDiarization = false;

    private Integer maxSpeakers;

    @Builder.Default
    private List<String> targetLanguages = new ArrayList<>();

    @Builder.Default
    private boolean generateSummary = false;
}
