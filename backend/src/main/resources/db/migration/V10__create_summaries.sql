-- V10: Create summaries table

CREATE TABLE IF NOT EXISTS summaries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL UNIQUE REFERENCES transcription_jobs(id) ON DELETE CASCADE,
    summary_text TEXT NOT NULL,
    key_points_json TEXT,
    action_items_json TEXT,
    topics_json TEXT,
    chapters_json TEXT,
    llm_provider VARCHAR(50) NOT NULL DEFAULT 'local',
    llm_model VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_summaries_job_id ON summaries(job_id);
