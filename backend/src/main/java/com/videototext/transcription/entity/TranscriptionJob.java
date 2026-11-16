package com.videototext.transcription.entity;

import com.videototext.common.entity.BaseEntity;
import com.videototext.user.entity.User;
import com.videototext.video.entity.Video;
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
@Table(name = "transcription_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private String status = "QUEUED";

    @Builder.Default
    @Column(name = "asr_engine", nullable = false, length = 50)
    private String asrEngine = "faster-whisper";

    @Builder.Default
    @Column(name = "asr_model", nullable = false, length = 50)
    private String asrModel = "small";

    @Builder.Default
    @Column(name = "source_language", nullable = false, length = 20)
    private String sourceLanguage = "auto";

    @Builder.Default
    @Column(name = "compute_type", nullable = false, length = 30)
    private String computeType = "int8_float16";

    @Builder.Default
    @Column(name = "device", nullable = false, length = 20)
    private String device = "auto";

    @Builder.Default
    @Column(name = "batch_size", nullable = false)
    private int batchSize = 1;

    @Builder.Default
    @Column(name = "word_timestamps", nullable = false)
    private boolean wordTimestamps = true;

    @Builder.Default
    @Column(name = "priority", nullable = false)
    private int priority = 5;

    @Builder.Default
    @Column(name = "processing_mode", nullable = false, length = 20)
    private String processingMode = "local";

    @Builder.Default
    @Column(name = "total_chunks", nullable = false)
    private int totalChunks = 0;

    @Builder.Default
    @Column(name = "completed_chunks", nullable = false)
    private int completedChunks = 0;

    @Builder.Default
    @Column(name = "failed_chunks", nullable = false)
    private int failedChunks = 0;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Builder.Default
    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 3;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Builder.Default
    @Column(name = "current_stage", nullable = false, length = 50)
    private String currentStage = "INITIALIZED";

    @Builder.Default
    @Column(name = "progress_percent", nullable = false)
    private float progressPercent = 0.0f;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Builder.Default
    @Column(name = "processing_duration_ms")
    private Long processingDurationMs = 0L;
}
