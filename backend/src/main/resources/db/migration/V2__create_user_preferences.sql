-- V2: Create user_preferences table

CREATE TABLE IF NOT EXISTS user_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    default_language VARCHAR(10) NOT NULL DEFAULT 'en',
    processing_mode VARCHAR(20) NOT NULL DEFAULT 'local',
    preferred_asr_engine VARCHAR(50) NOT NULL DEFAULT 'faster-whisper',
    preferred_asr_model VARCHAR(50) NOT NULL DEFAULT 'small',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_preferences_user_id ON user_preferences(user_id);
