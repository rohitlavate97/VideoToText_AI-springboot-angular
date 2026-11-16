package com.videototext.transcription.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReassignSpeakerRequest {

    @NotNull(message = "Speaker ID is required")
    private UUID speakerId;
}
