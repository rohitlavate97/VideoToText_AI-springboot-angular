-- V5: Create transcription_jobs table

CREATE TABLE IF NOT EXISTS transcription_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    asr_engine VARCHAR(50) NOT NULL DEFAULT 'faster-whisper',
    asr_model VARCHAR(50) NOT NULL DEFAULT 'small',
    source_language VARCHAR(20) NOT NULL DEFAULT 'auto',
    compute_type VARCHAR(30) NOT NULL DEFAULT 'int8_float16',
    device VARCHAR(20) NOT NULL DEFAULT 'auto',
    batch_size INTEGER NOT NULL DEFAULT 1,
    word_timestamps BOOLEAN NOT NULL DEFAULT TRUE,
    priority INTEGER NOT NULL DEFAULT 5,
    processing_mode VARCHAR(20) NOT NULL DEFAULT 'local',
    total_chunks INTEGER NOT NULL DEFAULT 0,
    completed_chunks INTEGER NOT NULL DEFAULT 0,
    failed_chunks INTEGER NOT NULL DEFAULT 0,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    error_message TEXT,
    current_stage VARCHAR(50) NOT NULL DEFAULT 'INITIALIZED',
    progress_percent REAL NOT NULL DEFAULT 0.0,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    processing_duration_ms BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_jobs_user_id ON transcription_jobs(user_id);
CREATE INDEX IF NOT EXISTS idx_jobs_video_id ON transcription_jobs(video_id);
CREATE INDEX IF NOT EXISTS idx_jobs_status ON transcription_jobs(status);
CREATE INDEX IF NOT EXISTS idx_jobs_created_at ON transcription_jobs(created_at DESC);
