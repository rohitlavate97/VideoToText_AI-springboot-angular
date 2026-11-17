# VideoToText AI

> Enterprise-grade, AI-powered platform converting video files into structured, searchable, editable transcripts with speaker diarization, multilingual translation, chapter summaries, and subtitle exports.

---

## Architecture Overview

VideoToText AI is built with an asynchronous, decoupled, event-driven microservices architecture:

- **Frontend**: Angular 17 Single Page Application (Standalone Components, TypeScript, Angular Material 17, RxJS, Server-Sent Events).
- **Backend**: Spring Boot 3.5.0 REST API (Java 21, Spring Security 6, JJWT 0.12.x, Spring Data JPA, Flyway 10, OpenAPI / Swagger).
- **AI Processing Worker**: Python 3.12 (faster-whisper, PyAnnote Audio 3.1, FFmpeg, CTranslate2, Pika, FastAPI).
- **Message Broker**: RabbitMQ 3.13 with Dead Letter Queues (DLQ) and topic exchanges for asynchronous job orchestration.
- **Cache & State Store**: Redis 7 (idempotency locks, token revocation, job caching).
- **Relational Database**: PostgreSQL 16 (relational entities, Flyway migrations V1–V15).
- **Object Storage**: MinIO / AWS S3 (multipart chunked uploads, audio extraction, media storage).

---

## Key Features & Capabilities

- **Robust Video Ingestion**:
  - Direct multipart and chunked resumable uploads up to 10 GB.
  - Deep file verification (extension, MIME, magic bytes inspection) and duration enforcement ($\le 240$ min).
  - FFprobe metadata extraction (resolution, duration, codecs, frame rate, audio channels).
- **High-Performance ASR Pipeline**:
  - `faster-whisper` transcription powered by CTranslate2 with automatic GPU VRAM detection and CPU fallback.
  - EBU R128 two-pass loudness audio normalization (`-af loudnorm`) and VAD chunking with boundary overlap preservation.
  - Chronological chunk stitching with global timeline offset alignment and duplicate boundary phoneme elimination.
- **Advanced Speaker Diarization**:
  - PyAnnote Audio 3.1 integration with Hugging Face token support and pure Python acoustic vector fallback.
  - Maximal temporal overlap association assigning speakers to transcript segments.
  - Cross-chunk speaker reconciliation using 128-dimensional cosine similarity against running speaker centroids ($0.75$ threshold).
  - Interactive frontend speaker filter bar with segment counts, one-click speaker filtering, and inline speaker renaming.
- **Real-Time Job Orchestration & In-Place Editor**:
  - Asynchronous job execution via RabbitMQ topic exchanges (`videototext.exchange`).
  - Real-time progress updates streamed to the frontend via Server-Sent Events (SSE).
  - Interactive transcript editor with in-place text and timestamp modifications, start/end boundary validation ($0 \le \text{start} < \text{end}$), and speaker dropdown reassignment.
  - Full audit revision tracking backed by `transcript_revisions` (Flyway V14) and frontend revision history modal.
  - Keyword and phrase transcript search with synchronized video playback seeking.
- **Multilingual Translation & Timestamp Preservation**:
  - Pure Python dictionary-based and neural MarianMT/NLLB translation provider abstraction.
  - Full support for Hindi (`[HI]`) and Marathi (`[MR]`), along with Spanish, French, and German.
  - Quality assurance engine verifying length ratios, Devanagari Unicode integrity (`0x0900 - 0x097F`), and hallucination detection.
  - Strict preservation of start/end timestamps and diarized speaker attributions across all translated segments.
  - Dynamic frontend language switcher (`Original [EN]`, `हिन्दी [HI]`, `मराठी [MR]`) with instant on-demand translation triggers and synchronized player seeking.
- **AI Summary & Intelligent Insights Engine**:
  - Pluggable provider abstraction with strict privacy enforcement (`LOCAL_ONLY`, `CLOUD_ALLOWED`, `HYBRID`) blocking cloud leaks.
  - Multi-length summary synthesis (`short`, `medium`, `detailed`) and formats (`paragraph`, `bullets`, `executive`).
  - Key points extraction, action items detection with speaker attribution and timestamps, and topic clustering with relevance scores.
  - Chronological video chapter generation with synchronized timeline navigation (`seekTo()`).
  - Interactive Angular insights dashboard tab card and chapter navigation pill bar.
- **Subtitle Generation & Multi-Format Exports**:
  - Multi-format subtitle generation: SubRip (.srt), WebVTT (.vtt), Plain Text (.txt), and Structured JSON (.json).
  - Strict monotonic timestamp validation ($end \ge start + 0.1s$), negative clamping, and rollover formatting (`HH:MM:SS,mmm` and `HH:MM:SS.mmm`).
  - Speaker-labeled subtitles resolving diarized aliases to human-readable names (`<v Speaker>` voice tags for WebVTT and `[Speaker]:` prefixes).
  - Translated subtitles exporting across source and translated languages (Hindi, Marathi, Spanish, etc.).
  - S3/MinIO cloud persistence with compound uniqueness constraints and export metadata audit logging.
  - Angular quick-export action-bar menu and dedicated subtitle management card with one-click downloads.

---

## Repository Structure

```
VideoToText_AI-springboot-angular/
├── backend/                   # Spring Boot 3.5.0 (Java 21) REST API
│   ├── src/
│   │   ├── main/java/com/videototext/
│   │   │   ├── auth/          # Authentication & JWT security
│   │   │   ├── common/        # Shared DTOs, BaseEntity, Exception handling
│   │   │   ├── config/        # Security, RabbitMQ, Redis, S3, Swagger config
│   │   │   ├── export/        # Export audit entity, repository & DTOs
│   │   │   ├── speaker/       # Speaker diarization entities, service & APIs
│   │   │   ├── subtitle/      # Subtitle generation entity, service & REST APIs
│   │   │   ├── summary/       # AI summary & insights entity, service & REST APIs
│   │   │   ├── transcription/ # Transcription orchestration, SSE & consumers
│   │   │   ├── translation/   # Multilingual translation entities, service & APIs
│   │   │   ├── user/          # User entities, profile & preferences
│   │   │   └── video/         # Video upload, S3 storage, metadata extraction
│   │   └── main/resources/    # application.yml, db/migration (Flyway V1-V15)
│   ├── Dockerfile
│   └── pom.xml
│
├── frontend/                  # Angular 17 Single Page Application
│   ├── src/
│   │   ├── app/
│   │   │   ├── core/          # Guards, interceptors, models, services
│   │   │   └── features/      # Auth, dashboard, videos (upload, library, detail)
│   │   ├── assets/
│   │   └── styles.scss
│   ├── Dockerfile
│   └── package.json
│
├── ai-worker/                 # Python 3.12 AI Worker
│   ├── app/
│   │   ├── config/            # Pydantic WorkerSettings
│   │   ├── consumers/         # RabbitMQ consumer with DLQ handling
│   │   ├── diarization/       # PyAnnote & Acoustic engines, reconciler, associator
│   │   ├── health/            # FastAPI liveness and readiness probes
│   │   ├── messaging/         # Pika RabbitMQ client & event publisher
│   │   ├── models/            # Pydantic schemas (jobs, events, results)
│   │   ├── processors/        # VideoTranscriptionProcessor pipeline
│   │   ├── providers/         # faster-whisper & Mock ASR providers
│   │   ├── storage/           # S3 storage client
│   │   ├── subtitles/         # Subtitle formatters (SRT, VTT, TXT, JSON) & generator
│   │   ├── summarization/     # Summary provider abstraction, local NLP engine & insights
│   │   └── translation/       # Dictionary & neural translation engines & quality checks
│   ├── tests/                 # Comprehensive pytest test suite (73 tests)
│   ├── Dockerfile
│   └── requirements.txt
│
├── summary/                   # Per-phase milestone summaries
│   ├── phase0-summary.md      # Requirements & System Architecture
│   ├── phase1-summary.md      # Dev Environment & Microservices Skeleton
│   ├── phase2-summary.md      # Backend Foundation, Flyway V1-V15 & RabbitMQ
│   ├── phase3-summary.md      # Authentication & User Management
│   ├── phase4-summary.md      # Video Upload & Storage Management
│   ├── phase5-summary.md      # Python AI Worker Foundation
│   ├── phase6-summary.md      # ASR Engine & Speech-to-Text Pipeline
│   ├── phase7-summary.md      # Transcription Orchestration & Optimization
│   ├── phase8-summary.md      # Speaker Diarization
│   ├── phase9-summary.md      # Transcript Management & In-Place Editor
│   ├── phase10-summary.md     # Multilingual Translation Engine
│   ├── phase11-summary.md     # AI Summary & Intelligent Insights
│   └── phase12-summary.md     # Subtitle Generation & Multi-Format Exports
│
├── infrastructure/            # Deployment & Infra Configs
│   ├── docker/
│   ├── nginx/
│   │   └── nginx.conf
│   └── scripts/
│
├── docs/                      # Architectural & Technical Documentation
│   ├── adr/                   # Architecture Decision Records
│   ├── api-specification.md   # Complete REST & SSE API contracts
│   ├── architecture.md        # System architecture & sequence diagrams
│   ├── changelog.md           # Project changelog
│   ├── database-design.md     # Database ERD & schema specs
│   ├── developer-guide.md     # Setup & onboarding guide
│   ├── performance-strategy.md# 15-minute SLA & benchmark plan
│   ├── project-progress.md    # Phase-by-phase tracker
│   ├── requirements.md        # Functional & non-functional requirements
│   ├── security.md            # Security, privacy & compliance
│   └── testing-strategy.md    # Test pyramid & key scenarios
│
├── docker-compose.yml         # Local dev infra (Postgres, RabbitMQ, Redis, MinIO)
├── .env.example               # Environment variable template
├── .gitignore
└── README.md
```

---

## Quick Start (Local Development)

### 1. Prerequisites
- **Java**: 21 LTS (`java -version`)
- **Maven**: 3.9+ (`mvn -version`)
- **Node.js**: 20 LTS & npm (`node -v`, `npm -v`)
- **Docker & Docker Compose**: Docker Desktop with Compose v2+
- **Python**: 3.12 (`python --version`)

### 2. Environment Setup
```bash
cp .env.example .env
```

### 3. Start Infrastructure Services
```bash
docker compose up -d postgres rabbitmq redis minio minio-init
```
Verify running containers:
- **PostgreSQL**: `localhost:5432` (db: `videototext`, user: `videototext`)
- **RabbitMQ Management**: `http://localhost:15672` (user/pass: `videototext`/`videototext_password`)
- **Redis**: `localhost:6379`
- **MinIO Console**: `http://localhost:9001` (user/pass: `minioadmin`/`minioadmin`)

### 4. Run Backend
```bash
cd backend
mvn clean test
mvn spring-boot:run
```
- API Docs (Swagger UI): `http://localhost:8080/swagger-ui.html`
- Health Endpoint: `http://localhost:8080/actuator/health`

### 5. Run Frontend
```bash
cd frontend
npm install
npm run build
npm start
```
- Application UI: `http://localhost:4200`

### 6. Run AI Worker
```bash
cd ai-worker
python -m venv .venv
# Windows: .venv\Scripts\activate | Linux: source .venv/bin/activate
pip install -r requirements.txt
pytest
python -m app.main
```
- Health Probe: `http://localhost:8000/health`

---

## Test Verification

| Module | Framework / Tool | Test Count | Status |
|--------|------------------|------------|--------|
| **Backend** | Spring Boot Test / MockMvc / JUnit 5 | 62 tests | ✅ 100% Passing |
| **AI Worker** | Pytest / Asyncio | 53 tests | ✅ 100% Passing |
| **Frontend** | Angular CLI Build (`ng build`) | Production Bundle | ✅ 0 Errors |

---

## Git Workflow & Branching Strategy

- **`main`**: Production-ready, released code.
- **`develop`**: Primary integration branch.
- **Conventional Commits**: `feat(...)`, `fix(...)`, `test(...)`, `chore(...)`, `docs(...)`, `perf(...)`.
- At the completion of each development phase, all changes are committed, pushed to `develop`, and merged into `main` (`--ff-only`).

---

## Project Status

| Phase | Description | Status |
|---|---|---|
| **Phase 0** | Product Requirements & Architecture | ✅ Completed |
| **Phase 1** | Development Environment & Repository Setup | ✅ Completed |
| **Phase 2** | Backend Foundation | ✅ Completed |
| **Phase 3** | Authentication & User Management | ✅ Completed |
| **Phase 4** | Video Upload & Storage Management | ✅ Completed |
| **Phase 5** | Python AI Worker Foundation | ✅ Completed |
| **Phase 6** | ASR Engine & Speech-to-Text Pipeline | ✅ Completed |
| **Phase 7** | Transcription Orchestration & Performance Optimization | ✅ Completed |
| **Phase 8** | Speaker Diarization | ✅ Completed |
| **Phase 9** | Transcript Management & Editor | ✅ Completed |
| **Phase 10** | Multilingual Translation | ✅ Completed |
| **Phase 11** | AI Summary & Intelligent Insights | ✅ Completed |
| **Phase 12** | Subtitle Generation & Exports | ✅ Completed |
| Phase 13 | Angular Dashboard & Complete UX | ⬜ Next |

---

## Phase Summaries Index

Every milestone is documented in full detail with architectural decisions, schemas, endpoint contracts, UI components, and test results:

- [Phase 0 Summary: Product Requirements & Architecture](summary/phase0-summary.md)
- [Phase 1 Summary: Development Environment & Repository Setup](summary/phase1-summary.md)
- [Phase 2 Summary: Backend Foundation & Infrastructure](summary/phase2-summary.md)
- [Phase 3 Summary: Authentication & User Management](summary/phase3-summary.md)
- [Phase 4 Summary: Video Upload & Storage Management](summary/phase4-summary.md)
- [Phase 5 Summary: Python AI Worker Foundation](summary/phase5-summary.md)
- [Phase 6 Summary: ASR Engine & Speech-to-Text Pipeline](summary/phase6-summary.md)
- [Phase 7 Summary: Transcription Orchestration & Performance Optimization](summary/phase7-summary.md)
- [Phase 8 Summary: Speaker Diarization](summary/phase8-summary.md)
- [Phase 9 Summary: Transcript Management & In-Place Editor](summary/phase9-summary.md)
- [Phase 10 Summary: Multilingual Translation Engine](summary/phase10-summary.md)
- [Phase 11 Summary: AI Summary & Intelligent Insights Engine](summary/phase11-summary.md)
- [Phase 12 Summary: Subtitle Generation & Multi-Format Exports](summary/phase12-summary.md)

---

## Next Milestone: Phase 13 — Angular Dashboard and Complete User Experience

The upcoming development session will implement:
- **Interactive Analytics Dashboard**: Total video volume, transcription duration stats, language breakdown chart, and status gauges.
- **Audio Waveform Visualizer**: Interactive zoomable audio waveform with playback position marker and region highlighting.
- **Enhanced Video Card Actions**: Batch export, multi-select deletion, and quick status filters.
- **UX Polish & Responsiveness**: Mobile-friendly navigation drawer, theme customizations, and keyboard shortcuts.

---

See [docs/project-progress.md](docs/project-progress.md) for the full phase-wise tracker and [docs/changelog.md](docs/changelog.md) for detailed release logs.
