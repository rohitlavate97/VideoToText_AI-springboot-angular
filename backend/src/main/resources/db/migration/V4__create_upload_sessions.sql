-- V4: Create upload_sessions table

CREATE TABLE IF NOT EXISTS upload_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id UUID REFERENCES videos(id) ON DELETE CASCADE,
    upload_type VARCHAR(30) NOT NULL DEFAULT 'MULTIPART',
    status VARCHAR(30) NOT NULL DEFAULT 'INITIATED',
    bytes_uploaded BIGINT NOT NULL DEFAULT 0,
    total_bytes BIGINT NOT NULL,
    parts_completed INTEGER NOT NULL DEFAULT 0,
    total_parts INTEGER NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_upload_sessions_video_id ON upload_sessions(video_id);
CREATE INDEX IF NOT EXISTS idx_upload_sessions_status ON upload_sessions(status);
