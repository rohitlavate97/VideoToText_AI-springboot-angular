# Database Design — VideoToText AI

## 1. Design Principles

- UUID primary keys for all entities (portability, no sequential exposure).
- `created_at` and `updated_at` timestamps on all tables.
- Proper foreign key constraints with cascading deletes where appropriate.
- Indexes on frequently queried columns (user_id, status, created_at).
- Flyway-managed migrations — never modify an applied migration.
- Soft delete not used — prefer hard delete with audit logging.

---

## 2. Entity Relationship Diagram

```mermaid
erDiagram
    users ||--o{ user_roles : has
    users ||--o| user_preferences : has
    users ||--o{ videos : uploads
    users ||--o{ transcription_jobs : creates

    roles ||--o{ user_roles : assigned_to

    videos ||--o{ upload_sessions : has
    videos ||--o{ transcription_jobs : triggers

    transcription_jobs ||--o{ transcription_chunks : contains
    transcription_jobs ||--o{ transcript_segments : produces
    transcription_jobs ||--o{ speakers : identifies
    transcription_jobs ||--o{ translations : generates
    transcription_jobs ||--o| summaries : generates
    transcription_jobs ||--o{ subtitles : generates
    transcription_jobs ||--o{ exports : produces
    transcription_jobs ||--o{ processing_events : emits
    transcription_jobs ||--o{ transcript_revisions : tracks

    transcription_chunks ||--o{ transcript_segments : contains

    transcript_segments }o--o| speakers : spoken_by

    translations }o--|| transcription_jobs : belongs_to

    users {
        uuid id PK
        string email UK
        string password_hash
        string first_name
        string last_name
        boolean enabled
        timestamp created_at
        timestamp updated_at
    }

    roles {
        uuid id PK
        string name UK
    }

    user_roles {
        uuid user_id FK
        uuid role_id FK
    }

    user_preferences {
        uuid id PK
        uuid user_id FK
        string default_language
        string processing_mode
        string preferred_asr_engine
        string preferred_asr_model
        timestamp created_at
        timestamp updated_at
    }

    videos {
        uuid id PK
        uuid user_id FK
        string original_filename
        string storage_key
        string content_type
        bigint file_size_bytes
        int duration_seconds
        string codec
        string resolution
        string status
        timestamp created_at
        timestamp updated_at
    }

    upload_sessions {
        uuid id PK
        uuid video_id FK
        string upload_type
        string status
        bigint bytes_uploaded
        bigint total_bytes
        int parts_completed
        int total_parts
        timestamp started_at
        timestamp completed_at
        timestamp created_at
        timestamp updated_at
    }

    transcription_jobs {
        uuid id PK
        uuid video_id FK
        uuid user_id FK
        string status
        string asr_engine
        string asr_model
        string source_language
        string compute_type
        string device
        int batch_size
        boolean word_timestamps
        int priority
        string processing_mode
        int total_chunks
        int completed_chunks
        int failed_chunks
        int retry_count
        int max_retries
        string error_message
        string current_stage
        float progress_percent
        timestamp started_at
        timestamp completed_at
        bigint processing_duration_ms
        timestamp created_at
        timestamp updated_at
    }

    transcription_chunks {
        uuid id PK
        uuid job_id FK
        int chunk_index
        float start_time_seconds
        float end_time_seconds
        float overlap_seconds
        string status
        string audio_storage_key
        int retry_count
        string error_message
        bigint processing_duration_ms
        timestamp started_at
        timestamp completed_at
        timestamp created_at
        timestamp updated_at
    }

    transcript_segments {
        uuid id PK
        uuid job_id FK
        uuid chunk_id FK
        uuid speaker_id FK
        int segment_index
        float start_time
        float end_time
        string text
        float confidence
        string language
        string word_timestamps_json
        timestamp created_at
        timestamp updated_at
    }

    speakers {
        uuid id PK
        uuid job_id FK
        string speaker_label
        string display_name
        string embedding_vector
        int total_speaking_time_ms
        int segment_count
        timestamp created_at
        timestamp updated_at
    }

    translations {
        uuid id PK
        uuid job_id FK
        string source_language
        string target_language
        string translation_engine
        string status
        string storage_key
        timestamp created_at
        timestamp updated_at
    }

    summaries {
        uuid id PK
        uuid job_id FK
        string summary_text
        string key_points_json
        string action_items_json
        string topics_json
        string chapters_json
        string llm_provider
        string llm_model
        timestamp created_at
        timestamp updated_at
    }

    subtitles {
        uuid id PK
        uuid job_id FK
        string format
        string language
        string storage_key
        boolean includes_speakers
        timestamp created_at
        timestamp updated_at
    }

    exports {
        uuid id PK
        uuid job_id FK
        uuid user_id FK
        string export_type
        string format
        string language
        string storage_key
        bigint file_size_bytes
        timestamp created_at
    }

    processing_events {
        uuid id PK
        uuid job_id FK
        string event_type
        string stage
        string message
        float progress_percent
        string metadata_json
        timestamp created_at
    }

    transcript_revisions {
        uuid id PK
        uuid job_id FK
        uuid user_id FK
        string revision_type
        uuid segment_id FK
        string field_changed
        string old_value
        string new_value
        timestamp created_at
    }
```

---

## 3. Table Details

### 3.1 `users`

Primary user account table.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | UUID | PK, default gen_random_uuid() | |
| email | VARCHAR(255) | UNIQUE, NOT NULL | Login identifier |
| password_hash | VARCHAR(255) | NOT NULL | bcrypt hash |
| first_name | VARCHAR(100) | NOT NULL | |
| last_name | VARCHAR(100) | NOT NULL | |
| enabled | BOOLEAN | NOT NULL, default TRUE | Account active flag |
| created_at | TIMESTAMPTZ | NOT NULL, default NOW() | |
| updated_at | TIMESTAMPTZ | NOT NULL, default NOW() | |

**Indexes**: `idx_users_email` on `email`.

### 3.2 `roles`

Static role definitions.

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| name | VARCHAR(50) | UNIQUE, NOT NULL |

Seed data: `ROLE_USER`, `ROLE_ADMIN`.

### 3.3 `user_roles`

Join table for many-to-many user–role relationship.

| Column | Type | Constraints |
|--------|------|-------------|
| user_id | UUID | FK → users(id) ON DELETE CASCADE |
| role_id | UUID | FK → roles(id) ON DELETE CASCADE |

**PK**: Composite (user_id, role_id).

### 3.4 `videos`

Uploaded video metadata.

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| id | UUID | PK | |
| user_id | UUID | FK → users(id), NOT NULL | Owner |
| original_filename | VARCHAR(500) | NOT NULL | User's filename |
| storage_key | VARCHAR(1000) | NOT NULL | S3 object key |
| content_type | VARCHAR(100) | NOT NULL | MIME type |
| file_size_bytes | BIGINT | NOT NULL | |
| duration_seconds | INTEGER | NOT NULL | FFprobe duration |
| codec | VARCHAR(50) | | Video codec |
| resolution | VARCHAR(20) | | e.g., "1920x1080" |
| status | VARCHAR(30) | NOT NULL | UPLOADED, PROCESSING, COMPLETED, FAILED, DELETED |
| created_at | TIMESTAMPTZ | NOT NULL | |
| updated_at | TIMESTAMPTZ | NOT NULL | |

**Indexes**: `idx_videos_user_id` on `user_id`, `idx_videos_status` on `status`.

### 3.5 `transcription_jobs`

Central job tracking table.

**Status values**: `QUEUED`, `PROCESSING`, `EXTRACTING_AUDIO`, `TRANSCRIBING`, `DIARIZING`, `TRANSLATING`, `SUMMARIZING`, `GENERATING_SUBTITLES`, `MERGING`, `COMPLETED`, `FAILED`, `CANCELLED`.

**Indexes**: `idx_jobs_user_id`, `idx_jobs_video_id`, `idx_jobs_status`, `idx_jobs_created_at`.

### 3.6 `transcription_chunks`

Individual chunk processing state.

**Status values**: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`.

**Indexes**: `idx_chunks_job_id`, `idx_chunks_status`.

### 3.7 `transcript_segments`

The actual transcript content — one row per spoken segment.

**Indexes**: `idx_segments_job_id`, `idx_segments_chunk_id`, `idx_segments_speaker_id`, `idx_segments_start_time`.

**Full-text search**: GIN index on `text` column for full-text search.

### 3.8 `speakers`

Identified speakers per transcription job.

**Indexes**: `idx_speakers_job_id`.

### 3.9 `translations`

Translated transcript versions.

**Status values**: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`.

**Indexes**: `idx_translations_job_id`.

### 3.10 `processing_events`

Append-only event log for processing progress tracking and SSE delivery.

**Indexes**: `idx_events_job_id`, `idx_events_created_at`.

**Partitioning consideration**: This table may grow large; consider time-based partitioning or TTL cleanup.

---

## 4. Migration Strategy

All schema changes use Flyway migrations:

```
backend/src/main/resources/db/migration/
├── V1__create_users_and_roles.sql
├── V2__create_user_preferences.sql
├── V3__create_videos.sql
├── V4__create_upload_sessions.sql
├── V5__create_transcription_jobs.sql
├── V6__create_transcription_chunks.sql
├── V7__create_transcript_segments.sql
├── V8__create_speakers.sql
├── V9__create_translations.sql
├── V10__create_summaries.sql
├── V11__create_subtitles.sql
├── V12__create_exports.sql
├── V13__create_processing_events.sql
├── V14__create_transcript_revisions.sql
├── V15__seed_roles.sql
```

**Rules**:
- Never modify an already-applied migration.
- Create a new migration for any schema change.
- Use `TIMESTAMPTZ` for all timestamps (timezone-aware).
- Use `gen_random_uuid()` for UUID defaults (PostgreSQL 13+).

---

## 5. Data Volume Estimates

For a 4-hour video:

| Entity | Estimated Rows | Notes |
|--------|---------------|-------|
| transcript_segments | 5,000 – 15,000 | ~1 segment per 1-3 seconds |
| speakers | 2 – 20 | Typical meeting/interview |
| transcription_chunks | 24 | 10-minute chunks |
| processing_events | 50 – 200 | Per-stage + per-chunk events |
| translations | 1 – 3 | Per target language |

**Storage**: A single 4-hour transcript (text only) is ~50-200 KB. Audio file is ~150-300 MB (16kHz mono WAV). Video file may be 1-10 GB.
