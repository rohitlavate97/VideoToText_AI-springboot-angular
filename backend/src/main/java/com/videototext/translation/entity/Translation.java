package com.videototext.translation.entity;

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
@Table(name = "translations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Translation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private TranscriptionJob job;

    @Column(name = "source_language", nullable = false, length = 20)
    private String sourceLanguage;

    @Column(name = "target_language", nullable = false, length = 20)
    private String targetLanguage;

    @Builder.Default
    @Column(name = "translation_engine", nullable = false, length = 50)
    private String translationEngine = "nllb";

    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "storage_key", length = 1000)
    private String storageKey;
}
