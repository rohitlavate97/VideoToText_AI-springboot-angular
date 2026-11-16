-- V9: Create translations table

CREATE TABLE IF NOT EXISTS translations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES transcription_jobs(id) ON DELETE CASCADE,
    source_language VARCHAR(20) NOT NULL,
    target_language VARCHAR(20) NOT NULL,
    translation_engine VARCHAR(50) NOT NULL DEFAULT 'nllb',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    storage_key VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_job_target_lang UNIQUE (job_id, target_language)
);

CREATE INDEX IF NOT EXISTS idx_translations_job_id ON translations(job_id);
