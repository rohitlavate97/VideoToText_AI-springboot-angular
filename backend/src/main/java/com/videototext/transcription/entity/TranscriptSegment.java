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

import java.util.UUID;

@Entity
@Table(name = "transcript_segments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptSegment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private TranscriptionJob job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chunk_id")
    private TranscriptionChunk chunk;

    @Column(name = "speaker_id")
    private UUID speakerId;

    @Column(name = "segment_index", nullable = false)
    private int segmentIndex;

    @Column(name = "start_time", nullable = false)
    private float startTime;

    @Column(name = "end_time", nullable = false)
    private float endTime;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Builder.Default
    @Column(name = "confidence")
    private float confidence = 1.0f;

    @Builder.Default
    @Column(name = "language", length = 20)
    private String language = "en";

    @Column(name = "word_timestamps_json", columnDefinition = "TEXT")
    private String wordTimestampsJson;
}
