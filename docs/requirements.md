# Product Requirements — VideoToText AI

## 1. Product Overview

VideoToText AI is a modern AI-powered platform that converts video files into structured, searchable, editable transcripts. It accepts videos, extracts audio, transcribes speech using interchangeable ASR engines, identifies speakers, generates timestamps, translates content, generates summaries, and exports subtitles.

---

## 2. Functional Requirements

### FR-01: User Management

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-01.1 | User registration with email and password | Must |
| FR-01.2 | User login with JWT authentication | Must |
| FR-01.3 | JWT refresh-token rotation | Must |
| FR-01.4 | Password hashing (bcrypt) | Must |
| FR-01.5 | User profile view and update | Must |
| FR-01.6 | Role-based authorization (USER, ADMIN) | Must |
| FR-01.7 | Secure logout (token invalidation) | Must |
| FR-01.8 | User preferences (language, processing mode) | Should |

### FR-02: Video Upload and Management

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-02.1 | Single video file upload | Must |
| FR-02.2 | Multipart upload for large files | Must |
| FR-02.3 | Resumable uploads | Should |
| FR-02.4 | File extension validation | Must |
| FR-02.5 | File content-type and magic-byte validation | Must |
| FR-02.6 | Video metadata extraction via FFprobe | Must |
| FR-02.7 | Duration validation — reject > 240 minutes | Must |
| FR-02.8 | Configurable file-size limit (default 10 GB) | Must |
| FR-02.9 | Upload to S3-compatible object storage | Must |
| FR-02.10 | Video ownership enforcement | Must |
| FR-02.11 | Video listing with pagination and filters | Must |
| FR-02.12 | Video deletion (cascading transcripts) | Must |
| FR-02.13 | Upload progress reporting | Must |
| FR-02.14 | Upload failure recovery | Should |
| FR-02.15 | Drag-and-drop upload UI | Must |

**Supported formats:** MP4, MKV, AVI, MOV, WebM, FLV, WMV, MPEG, 3GP.

### FR-03: Transcription

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-03.1 | Asynchronous transcription job creation | Must |
| FR-03.2 | Audio extraction from video via FFmpeg | Must |
| FR-03.3 | Audio normalization (16kHz mono WAV) | Must |
| FR-03.4 | Voice Activity Detection (VAD) | Should |
| FR-03.5 | Audio chunking (~10-minute orchestration chunks) | Must |
| FR-03.6 | Chunk overlap for boundary continuity | Must |
| FR-03.7 | ASR engine transcription per chunk | Must |
| FR-03.8 | Segment-level timestamps | Must |
| FR-03.9 | Word-level timestamps (where engine supports) | Should |
| FR-03.10 | Language detection | Must |
| FR-03.11 | Chunk transcript merging | Must |
| FR-03.12 | Duplicate removal at chunk boundaries | Must |
| FR-03.13 | Global timestamp preservation | Must |
| FR-03.14 | Persist each chunk independently | Must |
| FR-03.15 | Retry failed chunks without reprocessing successes | Must |
| FR-03.16 | Job recovery after worker restart | Must |
| FR-03.17 | Configurable ASR engine selection | Must |
| FR-03.18 | Configurable model, language, compute type, device, batch size | Must |

### FR-04: Speaker Diarization

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-04.1 | Speaker change detection | Must |
| FR-04.2 | Speaker ID assignment per segment | Must |
| FR-04.3 | Cross-chunk speaker identity reconciliation | Must |
| FR-04.4 | Unknown/overlapping speaker handling | Should |
| FR-04.5 | Speaker metadata storage | Must |
| FR-04.6 | Configurable expected speaker count | Should |

### FR-05: Translation

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-05.1 | Translate transcript segments | Must |
| FR-05.2 | Priority languages: English, Hindi, Marathi | Must |
| FR-05.3 | Preserve timestamps in translated output | Must |
| FR-05.4 | Preserve speaker IDs in translated output | Must |
| FR-05.5 | Multiple target languages per transcript | Should |
| FR-05.6 | Translation provider abstraction | Must |
| FR-05.7 | Translation quality validation | Should |

### FR-06: AI Summary and Insights

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-06.1 | Generate executive summary | Must |
| FR-06.2 | Extract key points | Must |
| FR-06.3 | Extract action items | Should |
| FR-06.4 | Identify topics | Should |
| FR-06.5 | Generate chapter headings with timestamps | Must |
| FR-06.6 | Configurable summary length | Should |
| FR-06.7 | LLM provider abstraction | Must |

### FR-07: Subtitle Generation and Export

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-07.1 | Generate SRT subtitles | Must |
| FR-07.2 | Generate VTT subtitles | Must |
| FR-07.3 | Export plain text transcript | Must |
| FR-07.4 | Export structured JSON | Must |
| FR-07.5 | Translated subtitles | Must |
| FR-07.6 | Speaker-labeled subtitles | Should |
| FR-07.7 | Downloadable export files | Must |

### FR-08: Transcript Management and Editor

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-08.1 | View transcript with timestamps and speakers | Must |
| FR-08.2 | Edit transcript text | Must |
| FR-08.3 | Edit timestamps | Should |
| FR-08.4 | Change speaker assignments | Must |
| FR-08.5 | Full-text search within transcript | Must |
| FR-08.6 | Click-to-navigate to video timestamp | Must |
| FR-08.7 | Autosave or explicit save | Must |
| FR-08.8 | Transcript revision history | Should |
| FR-08.9 | Pagination for large transcripts | Must |

### FR-09: Real-Time Processing Updates

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-09.1 | Server-Sent Events (SSE) for live status | Must |
| FR-09.2 | Current processing stage display | Must |
| FR-09.3 | Percentage progress (based on actual work) | Must |
| FR-09.4 | Chunk completion status | Must |
| FR-09.5 | Error and retry status display | Must |
| FR-09.6 | Automatic reconnection on network drop | Must |

### FR-10: Dashboard

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-10.1 | User dashboard with job overview | Must |
| FR-10.2 | Recent videos and transcription status | Must |
| FR-10.3 | Quick actions (upload, view library) | Must |
| FR-10.4 | Usage statistics | Should |

---

## 3. Non-Functional Requirements

### NFR-01: Performance

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-01.1 | Maximum video duration | 240 minutes |
| NFR-01.2 | End-to-end processing (4hr video) | ≤ 15 minutes (engineering target) |
| NFR-01.3 | Real-time throughput factor | ≥ 16× |
| NFR-01.4 | API response time (non-upload) | < 200ms p95 |
| NFR-01.5 | Upload throughput | Limited by network, not application |
| NFR-01.6 | Concurrent job support | ≥ 1 (define exact level with benchmarks) |

> **Note**: The 15-minute target is an engineering acceptance criterion, not an assumed capability. It must be validated through repeatable benchmarks on specified hardware before being claimed.

### NFR-02: Scalability

| ID | Requirement |
|----|-------------|
| NFR-02.1 | Horizontally scalable Python AI workers |
| NFR-02.2 | Stateless backend (session in JWT) |
| NFR-02.3 | Object storage for media (not local filesystem in production) |
| NFR-02.4 | Message queue decoupling (RabbitMQ) |

### NFR-03: Reliability

| ID | Requirement |
|----|-------------|
| NFR-03.1 | Graceful worker crash recovery |
| NFR-03.2 | Failed chunk retry without full reprocessing |
| NFR-03.3 | Dead-letter queue for poison messages |
| NFR-03.4 | Idempotent job processing |
| NFR-03.5 | Job cancellation support |
| NFR-03.6 | Database transaction integrity |

### NFR-04: Security

| ID | Requirement |
|----|-------------|
| NFR-04.1 | JWT-based stateless authentication |
| NFR-04.2 | Password hashing with bcrypt (cost ≥ 12) |
| NFR-04.3 | File signature validation (magic bytes) |
| NFR-04.4 | Path traversal prevention |
| NFR-04.5 | Sandboxed FFmpeg execution |
| NFR-04.6 | API rate limiting |
| NFR-04.7 | Upload quotas per user |
| NFR-04.8 | No silent cloud data transfer without consent |
| NFR-04.9 | Secrets management (no hardcoded credentials) |
| NFR-04.10 | Audit logging for security events |
| NFR-04.11 | HTTPS in production |

### NFR-05: Usability

| ID | Requirement |
|----|-------------|
| NFR-05.1 | Responsive design (desktop, tablet, mobile) |
| NFR-05.2 | Loading, error, and empty state handling |
| NFR-05.3 | Toast notifications for user feedback |
| NFR-05.4 | Accessible forms and controls (WCAG 2.1 AA) |
| NFR-05.5 | Intuitive drag-and-drop upload |

### NFR-06: Maintainability

| ID | Requirement |
|----|-------------|
| NFR-06.1 | Modular monolith architecture |
| NFR-06.2 | Clean code (SOLID, constructor injection, DTOs) |
| NFR-06.3 | Flyway migrations for schema changes |
| NFR-06.4 | Comprehensive test coverage |
| NFR-06.5 | OpenAPI documentation |
| NFR-06.6 | Conventional Commits |

### NFR-07: Deployment

| ID | Requirement |
|----|-------------|
| NFR-07.1 | Docker containerization for all components |
| NFR-07.2 | Docker Compose for local development |
| NFR-07.3 | CI/CD via GitHub Actions |
| NFR-07.4 | Environment-specific configuration |
| NFR-07.5 | Nginx reverse proxy |
| NFR-07.6 | Logging and monitoring (Actuator + structured logs) |

---

## 4. Assumptions

1. Single-tenant deployment initially (one organization).
2. Solo developer workflow — branching kept practical.
3. Local development on Windows with RTX 3060 Laptop GPU (6 GB VRAM, 16 GB RAM).
4. Production GPU processing on cloud infrastructure (L4/L40S/A100).
5. English, Hindi, and Marathi are the initial priority languages.
6. The 15-minute SLA applies to cloud GPU processing, not the local dev laptop.

---

## 5. Out of Scope (v1.0)

1. Live/streaming video transcription.
2. Multi-tenant SaaS with billing.
3. Mobile native applications.
4. Video editing or re-encoding.
5. Real-time collaborative editing.
6. OAuth2/social login (may be added later).
7. Kubernetes orchestration (Docker Compose is sufficient for v1.0).

---

## 6. Technical Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| 6 GB VRAM insufficient for large-v3 model | Cannot run large models locally | Start with small/base models; use cloud GPU for large models |
| 15-minute SLA infeasible on single GPU | Missed performance target | Benchmark early; design for multi-GPU scaling; accept revised SLA with evidence |
| Cross-chunk speaker reconciliation inaccurate | Incorrect speaker labels | Embedding-based speaker matching; configurable expected speaker count |
| pyannote.audio licensing restrictions | Cannot use commercially | Evaluate license; prepare alternative (NeMo diarization) |
| Translation quality for Hindi/Marathi | Poor user experience | Evaluate multiple translation engines; accept human post-editing |
| Large file upload failures | User frustration | Resumable uploads; chunked upload with retry |
| RabbitMQ message loss | Lost processing jobs | Publisher confirms; consumer ack; persistent queues; DLQ |
| GPU OOM during inference | Worker crash | Memory monitoring; conservative batch sizes; automatic fallback |
