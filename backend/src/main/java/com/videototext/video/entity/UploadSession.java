package com.videototext.video.entity;

import com.videototext.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "upload_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id")
    private Video video;

    @Builder.Default
    @Column(name = "upload_type", nullable = false, length = 30)
    private String uploadType = "MULTIPART";

    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private String status = "INITIATED";

    @Builder.Default
    @Column(name = "bytes_uploaded", nullable = false)
    private long bytesUploaded = 0L;

    @Column(name = "total_bytes", nullable = false)
    private long totalBytes;

    @Builder.Default
    @Column(name = "parts_completed", nullable = false)
    private int partsCompleted = 0;

    @Column(name = "total_parts", nullable = false)
    private int totalParts;

    @Column(name = "upload_id", length = 255)
    private String uploadId; // S3 uploadId

    @Builder.Default
    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;
}
