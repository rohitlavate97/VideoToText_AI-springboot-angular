-- V14: Create transcript_revisions table

CREATE TABLE IF NOT EXISTS transcript_revisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES transcription_jobs(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    revision_type VARCHAR(50) NOT NULL,
    segment_id UUID REFERENCES transcript_segments(id) ON DELETE CASCADE,
    field_changed VARCHAR(50) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_revisions_job_id ON transcript_revisions(job_id);
CREATE INDEX IF NOT EXISTS idx_revisions_user_id ON transcript_revisions(user_id);
