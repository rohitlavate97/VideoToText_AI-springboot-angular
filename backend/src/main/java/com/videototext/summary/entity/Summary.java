package com.videototext.summary.entity;

import com.videototext.common.entity.BaseEntity;
import com.videototext.transcription.entity.TranscriptionJob;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "summaries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Summary extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false, unique = true)
    private TranscriptionJob job;

    @Column(name = "summary_text", nullable = false, columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "key_points_json", columnDefinition = "TEXT")
    private String keyPointsJson;

    @Column(name = "action_items_json", columnDefinition = "TEXT")
    private String actionItemsJson;

    @Column(name = "topics_json", columnDefinition = "TEXT")
    private String topicsJson;

    @Column(name = "chapters_json", columnDefinition = "TEXT")
    private String chaptersJson;

    @Builder.Default
    @Column(name = "llm_provider", nullable = false, length = 50)
    private String llmProvider = "local";

    @Column(name = "llm_model", length = 50)
    private String llmModel;
}
