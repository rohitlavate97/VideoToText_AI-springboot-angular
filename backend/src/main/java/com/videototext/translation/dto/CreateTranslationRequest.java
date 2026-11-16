package com.videototext.translation.dto;

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
public class CreateTranslationRequest {

    @NotBlank(message = "Target language is required")
    @Size(min = 2, max = 10, message = "Target language code must be 2 to 10 characters")
    private String targetLanguage;

    private String engine;
}
