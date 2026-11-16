-- V7: Create transcript_segments table

CREATE TABLE IF NOT EXISTS transcript_segments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES transcription_jobs(id) ON DELETE CASCADE,
    chunk_id UUID REFERENCES transcription_chunks(id) ON DELETE SET NULL,
    speaker_id UUID,
    segment_index INTEGER NOT NULL,
    start_time REAL NOT NULL,
    end_time REAL NOT NULL,
    text TEXT NOT NULL,
    confidence REAL DEFAULT 1.0,
    language VARCHAR(20) DEFAULT 'en',
    word_timestamps_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_job_segment_index UNIQUE (job_id, segment_index)
);

CREATE INDEX IF NOT EXISTS idx_segments_job_id ON transcript_segments(job_id);
CREATE INDEX IF NOT EXISTS idx_segments_chunk_id ON transcript_segments(chunk_id);
CREATE INDEX IF NOT EXISTS idx_segments_speaker_id ON transcript_segments(speaker_id);
CREATE INDEX IF NOT EXISTS idx_segments_start_time ON transcript_segments(job_id, start_time);

-- Full-text search index on segment text
CREATE INDEX IF NOT EXISTS idx_segments_text_gin ON transcript_segments USING gin(to_tsvector('english', text));
