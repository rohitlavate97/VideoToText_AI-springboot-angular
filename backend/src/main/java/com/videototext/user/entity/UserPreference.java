package com.videototext.user.entity;

import com.videototext.common.entity.BaseEntity;
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
@Table(name = "user_preferences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreference extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder.Default
    @Column(name = "default_language", nullable = false, length = 10)
    private String defaultLanguage = "en";

    @Builder.Default
    @Column(name = "processing_mode", nullable = false, length = 20)
    private String processingMode = "local";

    @Builder.Default
    @Column(name = "preferred_asr_engine", nullable = false, length = 50)
    private String preferredAsrEngine = "faster-whisper";

    @Builder.Default
    @Column(name = "preferred_asr_model", nullable = false, length = 50)
    private String preferredAsrModel = "small";
}
