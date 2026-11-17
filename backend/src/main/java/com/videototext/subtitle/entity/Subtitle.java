package com.videototext.subtitle.entity;

import com.videototext.common.entity.BaseEntity;
import com.videototext.transcription.entity.TranscriptionJob;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "subtitles",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_job_format_lang_speakers",
                        columnNames = {"job_id", "format", "language", "includes_speakers"}
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subtitle extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private TranscriptionJob job;

    @Column(name = "format", nullable = false, length = 10)
    private String format;

    @Builder.Default
    @Column(name = "language", nullable = false, length = 20)
    private String language = "en";

    @Column(name = "storage_key", nullable = false, length = 1000)
    private String storageKey;

    @Builder.Default
    @Column(name = "includes_speakers", nullable = false)
    private boolean includesSpeakers = false;
}
