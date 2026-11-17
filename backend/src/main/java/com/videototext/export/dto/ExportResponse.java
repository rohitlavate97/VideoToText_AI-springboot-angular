package com.videototext.export.dto;

import com.videototext.export.entity.Export;
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
public class ExportResponse {

    private UUID id;
    private UUID jobId;
    private UUID userId;
    private String exportType;
    private String format;
    private String language;
    private String storageKey;
    private long fileSizeBytes;
    private Instant createdAt;

    public static ExportResponse fromEntity(Export entity) {
        if (entity == null) {
            return null;
        }
        return ExportResponse.builder()
                .id(entity.getId())
                .jobId(entity.getJob().getId())
                .userId(entity.getUser().getId())
                .exportType(entity.getExportType())
                .format(entity.getFormat())
                .language(entity.getLanguage())
                .storageKey(entity.getStorageKey())
                .fileSizeBytes(entity.getFileSizeBytes())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
