-- V13: Create processing_events table

CREATE TABLE IF NOT EXISTS processing_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES transcription_jobs(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    stage VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    progress_percent REAL NOT NULL DEFAULT 0.0,
    metadata_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_events_job_id ON processing_events(job_id);
CREATE INDEX IF NOT EXISTS idx_events_created_at ON processing_events(created_at ASC);
