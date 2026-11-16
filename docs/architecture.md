# System Architecture — VideoToText AI

## 1. Architecture Overview

VideoToText AI follows a **modular monolith** backend architecture with an **asynchronous distributed processing** pipeline. The system is composed of three main deployable units:

1. **Angular Frontend** — SPA serving the user interface.
2. **Spring Boot Backend** — API gateway, orchestration, data management, authentication.
3. **Python AI Worker** — GPU-accelerated audio processing, ASR, diarization, translation, summarization.

Communication between the backend and AI worker is fully decoupled via **RabbitMQ**, enabling independent scaling and deployment.

---

## 2. System Architecture Diagram

```mermaid
graph TD
    subgraph "Client Layer"
        UI["Angular 17 SPA"]
    end

    subgraph "Reverse Proxy"
        NGINX["Nginx"]
    end

    subgraph "Application Layer"
        API["Spring Boot 3.5.x<br/>REST API + SSE"]
    end

    subgraph "Data Layer"
        PG["PostgreSQL"]
        REDIS["Redis<br/>Cache + Sessions"]
        S3["S3-Compatible<br/>Object Storage"]
    end

    subgraph "Message Broker"
        RMQ["RabbitMQ"]
        DLQ["Dead Letter Queue"]
    end

    subgraph "AI Processing Layer"
        WORKER["Python AI Worker"]
        FFMPEG["FFmpeg / FFprobe"]
        ASR["ASR Engines<br/>faster-whisper<br/>Whisper<br/>Custom ASR"]
        DIAR["Speaker Diarization<br/>pyannote.audio"]
        TRANS["Translation Engine"]
        SUMM["Summarization<br/>LLM Provider"]
    end

    UI -->|"HTTPS"| NGINX
    NGINX -->|"Proxy"| API
    API -->|"JDBC"| PG
    API -->|"Cache/Pub"| REDIS
    API -->|"Upload/Download"| S3
    API -->|"Publish Jobs"| RMQ
    API -->|"SSE"| UI

    RMQ -->|"Consume Jobs"| WORKER
    RMQ --- DLQ
    WORKER --> FFMPEG
    WORKER --> ASR
    WORKER --> DIAR
    WORKER --> TRANS
    WORKER --> SUMM
    WORKER -->|"Store Results"| S3
    WORKER -->|"Update Status"| RMQ
    RMQ -->|"Status Events"| API
    WORKER -->|"Direct DB Write"| PG
```

---

## 3. Component Responsibilities

### 3.1 Angular Frontend

| Responsibility | Details |
|----------------|---------|
| User interface | Login, registration, dashboard, upload, library, editor, exports |
| Authentication | JWT storage, auth interceptor, route guards |
| Upload management | Drag-and-drop, multipart upload, progress tracking |
| Real-time updates | SSE subscription for processing status |
| Transcript editor | View, edit, search, navigate by timestamp |
| Export center | Download SRT, VTT, TXT, JSON |

### 3.2 Spring Boot Backend

| Responsibility | Details |
|----------------|---------|
| Authentication & authorization | JWT issue/validate, refresh tokens, role-based access |
| Video management | Upload validation, metadata extraction, storage orchestration |
| Job orchestration | Create transcription jobs, publish to RabbitMQ, track status |
| Data management | CRUD for all entities via JPA/Hibernate |
| Real-time events | SSE endpoint for processing progress |
| Export generation | Subtitle formatting, file downloads |
| API gateway | OpenAPI docs, rate limiting, validation, error handling |
| Health & monitoring | Actuator endpoints, structured logging |

### 3.3 Python AI Worker

| Responsibility | Details |
|----------------|---------|
| Job consumption | RabbitMQ consumer with ack/nack/retry |
| Audio extraction | FFmpeg video-to-audio conversion |
| Audio preparation | Normalization, VAD, chunking |
| ASR transcription | Provider-based engine selection and execution |
| Speaker diarization | pyannote.audio integration |
| Translation | Transcript segment translation |
| Summarization | LLM-based summary generation |
| Subtitle generation | SRT/VTT formatting |
| Result persistence | Save to PostgreSQL and object storage |
| Status reporting | Publish progress events to RabbitMQ |

### 3.4 Infrastructure Components

| Component | Role |
|-----------|------|
| PostgreSQL | Primary relational database |
| Redis | Caching (user sessions, rate limiting, job status) |
| RabbitMQ | Async message broker between backend and AI worker |
| S3 / MinIO | Object storage for videos, audio, transcripts, exports |
| Nginx | Reverse proxy, TLS termination, static file serving |
| Docker | Containerization for all components |

---

## 4. Video Processing Sequence Diagram

```mermaid
sequenceDiagram
    actor User
    participant UI as Angular Frontend
    participant API as Spring Boot API
    participant DB as PostgreSQL
    participant Store as Object Storage
    participant Queue as RabbitMQ
    participant Worker as Python AI Worker
    participant FFmpeg as FFmpeg
    participant ASR as ASR Engine
    participant Diar as Diarization
    participant Trans as Translation
    participant LLM as Summary LLM

    User->>UI: Upload video file
    UI->>API: POST /api/v1/videos/upload (multipart)
    API->>API: Validate file (extension, magic bytes, size)
    API->>FFmpeg: FFprobe metadata extraction
    FFmpeg-->>API: Duration, codec, resolution
    API->>API: Reject if duration > 240 min
    API->>Store: Upload video to object storage
    Store-->>API: Storage key
    API->>DB: Create video record
    API-->>UI: 201 Created (videoId)

    User->>UI: Request transcription
    UI->>API: POST /api/v1/transcriptions
    API->>DB: Create transcription job (QUEUED)
    API->>Queue: Publish transcription.request
    API-->>UI: 202 Accepted (jobId)

    UI->>API: Subscribe SSE /api/v1/events/jobs/{jobId}

    Queue->>Worker: Consume transcription.request
    Worker->>DB: Update job status (PROCESSING)
    Worker->>Queue: Publish progress event
    Queue->>API: Forward progress
    API->>UI: SSE: stage=AUDIO_EXTRACTION

    Worker->>Store: Download video
    Worker->>FFmpeg: Extract audio (16kHz mono WAV)
    FFmpeg-->>Worker: Audio file
    Worker->>Worker: VAD + chunking (24 × ~10 min)

    loop For each chunk
        Worker->>ASR: Transcribe chunk
        ASR-->>Worker: Segments with timestamps
        Worker->>DB: Save chunk result
        Worker->>Queue: Publish chunk progress
        Queue->>API: Forward progress
        API->>UI: SSE: chunk 5/24 complete
    end

    Worker->>Diar: Speaker diarization (full audio)
    Diar-->>Worker: Speaker segments
    Worker->>Worker: Align speakers with transcript
    Worker->>Worker: Reconcile speaker IDs across chunks

    Worker->>Worker: Merge all chunks
    Worker->>Worker: Remove boundary duplicates
    Worker->>Worker: Correct global timestamps

    Worker->>Trans: Translate transcript segments
    Trans-->>Worker: Translated segments

    Worker->>LLM: Generate summary + key points
    LLM-->>Worker: Summary, chapters, action items

    Worker->>Worker: Generate SRT + VTT subtitles
    Worker->>Store: Save all outputs
    Worker->>DB: Save transcript, speakers, translations, summary, subtitles
    Worker->>DB: Update job status (COMPLETED)
    Worker->>Queue: Publish completion event
    Queue->>API: Forward completion
    API->>UI: SSE: status=COMPLETED
    Worker->>Queue: ACK message

    User->>UI: View transcript
    UI->>API: GET /api/v1/transcriptions/{id}
    API->>DB: Fetch transcript + segments
    API-->>UI: Transcript data
```

---

## 5. Communication Patterns

### 5.1 Synchronous (REST)

- Frontend ↔ Backend: All user-facing API calls.
- Backend validates, persists, and returns responses.

### 5.2 Asynchronous (RabbitMQ)

**Exchanges and Queues:**

| Exchange | Queue | Routing Key | Purpose |
|----------|-------|-------------|---------|
| `videototext.exchange` | `transcription.requests` | `transcription.request` | New transcription jobs |
| `videototext.exchange` | `transcription.results` | `transcription.result` | Completed job results |
| `videototext.exchange` | `processing.events` | `processing.event` | Progress updates |
| `videototext.dlx` | `transcription.dlq` | `transcription.dead` | Failed messages |

**Message Flow:**
1. Backend publishes job → `transcription.requests` queue.
2. Worker consumes, processes, publishes progress → `processing.events` queue.
3. Backend consumes progress events → pushes via SSE to frontend.
4. Worker publishes completion → `transcription.results` queue.
5. Failed messages (max retries exceeded) → `transcription.dlq`.

### 5.3 Real-Time (SSE)

- Backend → Frontend: Server-Sent Events for live processing updates.
- Per-job SSE stream with automatic reconnection.

---

## 6. Package Structure

### 6.1 Spring Boot Backend

```
com.videototext
├── config/                  # Spring configuration classes
│   ├── SecurityConfig
│   ├── RabbitMQConfig
│   ├── RedisConfig
│   ├── JpaConfig
│   ├── SwaggerConfig
│   └── WebConfig
├── security/                # JWT, filters, auth utilities
│   ├── JwtTokenProvider
│   ├── JwtAuthenticationFilter
│   └── UserDetailsServiceImpl
├── auth/                    # Authentication endpoints
│   ├── AuthController
│   ├── AuthService
│   └── dto/
├── user/                    # User management
│   ├── UserController
│   ├── UserService
│   ├── UserRepository
│   ├── User (entity)
│   └── dto/
├── video/                   # Video upload & management
│   ├── VideoController
│   ├── VideoService
│   ├── VideoRepository
│   ├── Video (entity)
│   ├── validation/
│   └── dto/
├── storage/                 # Object storage abstraction
│   ├── StorageService
│   └── S3StorageService
├── transcription/           # Transcription job management
│   ├── TranscriptionController
│   ├── TranscriptionService
│   ├── TranscriptionJobRepository
│   ├── TranscriptionJob (entity)
│   ├── TranscriptSegment (entity)
│   ├── messaging/
│   └── dto/
├── speaker/                 # Speaker management
│   ├── SpeakerController
│   ├── SpeakerService
│   └── Speaker (entity)
├── translation/             # Translation management
│   ├── TranslationController
│   ├── TranslationService
│   └── Translation (entity)
├── summary/                 # Summary management
│   ├── SummaryController
│   ├── SummaryService
│   └── Summary (entity)
├── subtitle/                # Subtitle generation
│   ├── SubtitleService
│   └── SubtitleGenerator
├── export/                  # Export endpoints
│   ├── ExportController
│   └── ExportService
├── notification/            # SSE event management
│   ├── EventController
│   └── EventService
└── common/                  # Shared utilities
    ├── exception/
    ├── dto/
    └── util/
```

### 6.2 Python AI Worker

```
ai-worker/
├── app/
│   ├── __init__.py
│   ├── main.py                    # Worker entry point
│   ├── config/
│   │   ├── settings.py            # Pydantic settings
│   │   └── logging_config.py
│   ├── consumers/
│   │   └── transcription_consumer.py
│   ├── processors/
│   │   ├── pipeline.py            # Orchestration pipeline
│   │   ├── audio_extractor.py     # FFmpeg wrapper
│   │   ├── audio_normalizer.py
│   │   ├── chunker.py             # Audio chunking
│   │   └── merger.py              # Chunk merge + dedup
│   ├── providers/
│   │   ├── base.py                # ASRProvider interface
│   │   ├── faster_whisper_provider.py
│   │   ├── whisper_provider.py
│   │   └── registry.py            # Provider factory
│   ├── diarization/
│   │   ├── diarizer.py
│   │   └── speaker_reconciler.py
│   ├── translation/
│   │   ├── base.py
│   │   └── translator.py
│   ├── summarization/
│   │   ├── base.py
│   │   └── summarizer.py
│   ├── subtitles/
│   │   ├── srt_generator.py
│   │   └── vtt_generator.py
│   ├── storage/
│   │   └── s3_client.py
│   ├── messaging/
│   │   ├── publisher.py
│   │   └── schemas.py             # Pydantic message models
│   └── models/
│       └── domain.py              # Shared domain models
├── tests/
│   ├── test_pipeline.py
│   ├── test_providers.py
│   ├── test_chunker.py
│   └── test_merger.py
├── requirements.txt
├── Dockerfile
└── README.md
```

### 6.3 Angular Frontend

```
frontend/src/app/
├── core/
│   ├── auth/
│   │   ├── auth.service.ts
│   │   ├── auth.interceptor.ts
│   │   ├── auth.guard.ts
│   │   └── token.service.ts
│   ├── services/
│   │   ├── api.service.ts
│   │   └── notification.service.ts
│   └── models/
│       ├── user.model.ts
│       ├── video.model.ts
│       ├── transcription.model.ts
│       └── api-response.model.ts
├── features/
│   ├── auth/
│   │   ├── login/
│   │   └── register/
│   ├── dashboard/
│   ├── videos/
│   │   ├── upload/
│   │   ├── library/
│   │   └── detail/
│   ├── transcription/
│   │   ├── editor/
│   │   ├── progress/
│   │   └── search/
│   ├── translation/
│   ├── summary/
│   ├── export/
│   └── settings/
├── shared/
│   ├── components/
│   │   ├── header/
│   │   ├── sidebar/
│   │   ├── loading/
│   │   └── error/
│   ├── pipes/
│   └── directives/
└── layouts/
    ├── auth-layout/
    └── main-layout/
```

---

## 7. Deployment Architecture

### 7.1 Local Development

```mermaid
graph LR
    subgraph "Developer Machine"
        NG["ng serve :4200"]
        SB["Spring Boot :8080"]
        PY["Python Worker"]
    end

    subgraph "Docker Compose"
        PG["PostgreSQL :5432"]
        RMQ["RabbitMQ :5672/:15672"]
        RD["Redis :6379"]
        MINIO["MinIO :9000/:9001"]
    end

    NG --> SB
    SB --> PG
    SB --> RMQ
    SB --> RD
    SB --> MINIO
    PY --> RMQ
    PY --> PG
    PY --> MINIO
```

### 7.2 Production

```mermaid
graph LR
    subgraph "Edge"
        CDN["CDN / Static"]
        NGX["Nginx :443"]
    end

    subgraph "Application Tier"
        SB1["Spring Boot (container)"]
    end

    subgraph "Data Tier"
        PG["PostgreSQL (managed)"]
        RMQ["RabbitMQ (managed)"]
        RD["Redis (managed)"]
        S3["S3 Object Storage"]
    end

    subgraph "GPU Tier"
        W1["AI Worker (GPU)"]
    end

    CDN --> NGX
    NGX --> SB1
    SB1 --> PG
    SB1 --> RMQ
    SB1 --> RD
    SB1 --> S3
    W1 --> RMQ
    W1 --> PG
    W1 --> S3
```

---

## 8. Key Architectural Decisions

See `docs/adr/` for full Architecture Decision Records.

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Backend style | Modular monolith | Simpler deployment; microservices overhead unjustified for v1 |
| Backend-Worker communication | RabbitMQ | Decoupled async processing; reliable delivery; DLQ support |
| ASR engine coupling | Provider abstraction | Swap engines without code changes; benchmark multiple engines |
| Real-time updates | SSE (not WebSocket) | Simpler; unidirectional; sufficient for progress updates |
| Object storage | S3-compatible (MinIO local) | Scalable; production-ready with any S3 provider |
| Database migrations | Flyway | Version-controlled, reproducible schema changes |
| Authentication | JWT (access + refresh) | Stateless; scalable; no server-side session storage |
| Frontend framework | Angular 17 | Strong typing; enterprise patterns; reactive forms |
