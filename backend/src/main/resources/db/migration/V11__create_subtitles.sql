-- V11: Create subtitles table

CREATE TABLE IF NOT EXISTS subtitles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES transcription_jobs(id) ON DELETE CASCADE,
    format VARCHAR(10) NOT NULL,
    language VARCHAR(20) NOT NULL DEFAULT 'en',
    storage_key VARCHAR(1000) NOT NULL,
    includes_speakers BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_job_format_lang_speakers UNIQUE (job_id, format, language, includes_speakers)
);

CREATE INDEX IF NOT EXISTS idx_subtitles_job_id ON subtitles(job_id);
