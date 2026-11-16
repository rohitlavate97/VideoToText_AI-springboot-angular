package com.videototext.video.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateUploadResponse {

    private UUID uploadSessionId;
    private UUID videoId;
    private long partSize;
    private int totalParts;
}
