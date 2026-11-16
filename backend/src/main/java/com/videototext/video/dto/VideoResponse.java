package com.videototext.video.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoResponse {

    private UUID id;
    private String originalFilename;
    private long fileSizeBytes;
    private int durationSeconds;
    private String codec;
    private String resolution;
    private String contentType;
    private String status;
    private String downloadUrl;
    private Instant createdAt;
}
