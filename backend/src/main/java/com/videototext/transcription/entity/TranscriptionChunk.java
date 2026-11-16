package com.videototext.transcription.entity;

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
@Table(name = "transcription_chunks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptionChunk extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private TranscriptionJob job;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "start_time_seconds", nullable = false)
    private float startTimeSeconds;

    @Column(name = "end_time_seconds", nullable = false)
    private float endTimeSeconds;

    @Builder.Default
    @Column(name = "overlap_seconds", nullable = false)
    private float overlapSeconds = 0.0f;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "audio_storage_key", length = 1000)
    private String audioStorageKey;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Builder.Default
    @Column(name = "processing_duration_ms")
    private Long processingDurationMs = 0L;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
