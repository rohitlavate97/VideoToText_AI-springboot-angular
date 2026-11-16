-- V6: Create transcription_chunks table

CREATE TABLE IF NOT EXISTS transcription_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES transcription_jobs(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    start_time_seconds REAL NOT NULL,
    end_time_seconds REAL NOT NULL,
    overlap_seconds REAL NOT NULL DEFAULT 0.0,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    audio_storage_key VARCHAR(1000),
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    processing_duration_ms BIGINT DEFAULT 0,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_job_chunk_index UNIQUE (job_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_chunks_job_id ON transcription_chunks(job_id);
CREATE INDEX IF NOT EXISTS idx_chunks_status ON transcription_chunks(status);
