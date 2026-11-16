-- V8: Create speakers table and link to transcript_segments

CREATE TABLE IF NOT EXISTS speakers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES transcription_jobs(id) ON DELETE CASCADE,
    speaker_label VARCHAR(50) NOT NULL,
    display_name VARCHAR(100),
    embedding_vector TEXT,
    total_speaking_time_ms INTEGER NOT NULL DEFAULT 0,
    segment_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_job_speaker_label UNIQUE (job_id, speaker_label)
);

CREATE INDEX IF NOT EXISTS idx_speakers_job_id ON speakers(job_id);

-- Add foreign key constraint to transcript_segments
ALTER TABLE transcript_segments
    ADD CONSTRAINT fk_segments_speaker
    FOREIGN KEY (speaker_id) REFERENCES speakers(id) ON DELETE SET NULL;
