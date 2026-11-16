package com.videototext.translation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranslatedSegmentDto {

    private int segmentIndex;
    private float startTime;
    private float endTime;
    private String speakerLabel;
    private String sourceText;
    private String translatedText;
    private String targetLanguage;
}
