package com.videototext.speaker.entity;

import com.videototext.common.entity.BaseEntity;
import com.videototext.transcription.entity.TranscriptionJob;
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

@Entity
@Table(name = "speakers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Speaker extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private TranscriptionJob job;

    @Column(name = "speaker_label", nullable = false, length = 50)
    private String speakerLabel;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "embedding_vector", columnDefinition = "TEXT")
    private String embeddingVector;

    @Builder.Default
    @Column(name = "total_speaking_time_ms", nullable = false)
    private int totalSpeakingTimeMs = 0;

    @Builder.Default
    @Column(name = "segment_count", nullable = false)
    private int segmentCount = 0;
}
