package com.videototext.export.entity;

import com.videototext.transcription.entity.TranscriptionJob;
import com.videototext.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exports")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Export {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private TranscriptionJob job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "export_type", nullable = false, length = 30)
    private String exportType;

    @Column(name = "format", nullable = false, length = 10)
    private String format;

    @Builder.Default
    @Column(name = "language", nullable = false, length = 20)
    private String language = "en";

    @Column(name = "storage_key", nullable = false, length = 1000)
    private String storageKey;

    @Builder.Default
    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
