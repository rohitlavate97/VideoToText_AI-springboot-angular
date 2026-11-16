package com.videototext.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceResponse {

    private String defaultLanguage;
    private String processingMode;
    private String preferredAsrEngine;
    private String preferredAsrModel;
}
