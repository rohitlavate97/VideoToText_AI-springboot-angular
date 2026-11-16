package com.videototext.speaker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSpeakerRequest {

    @NotBlank(message = "displayName is required")
    @Size(min = 1, max = 100, message = "displayName must be between 1 and 100 characters")
    private String displayName;
}
