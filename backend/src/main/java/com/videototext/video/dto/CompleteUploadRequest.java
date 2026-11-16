package com.videototext.video.dto;

import com.videototext.storage.service.CompletedPartDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteUploadRequest {

    private List<CompletedPartDto> parts;
}
