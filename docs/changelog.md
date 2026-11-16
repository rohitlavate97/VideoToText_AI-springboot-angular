# Changelog — VideoToText AI

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Phase 0**: Requirements, architecture, database ERD, API specification, performance strategy, security, testing strategy, ADRs.
- **Phase 1**:
  - Spring Boot 3.5.0 backend initialized with Java 21, Maven 3.9, Flyway, PostgreSQL, Redis, RabbitMQ, S3, JWT, OpenAPI, Testcontainers.
  - Angular 17 frontend initialized with routing, SCSS, Angular Material 17.
  - Python 3.11 AI Worker skeleton initialized with Pydantic settings and Dockerfile.
  - Multi-service `docker-compose.yml` for PostgreSQL 16, RabbitMQ 3.13, Redis 7, and MinIO.
  - Nginx reverse proxy configuration with SSE streaming support.
- **Phase 2**:
  - Modular package structure across all domains.
  - Common DTOs (`ApiResponse`, `ErrorResponse`, `PageResponse`) and `BaseEntity` with JPA callbacks.
  - Standardized `ErrorCode` enum and `GlobalExceptionHandler` with automatic validation error mapping.
  - Complete baseline suite of 15 Flyway migrations (`V1` to `V15`).
  - RabbitMQ topic and dead-letter configurations, Redis template/cache manager, OpenAPI Swagger docs, and health checks.
- **Phase 3**:
  - `User`, `Role`, and `UserPreference` JPA entities with EAGER role fetching and audit timestamps.
  - `UserRepository`, `RoleRepository`, and `UserPreferenceRepository`.
  - `JwtTokenProvider` using JJWT 0.12.x for HMAC-SHA512 access token (15 mins) and refresh token (7 days) generation and validation.
  - `JwtAuthenticationFilter` and `JwtAuthenticationEntryPoint` wired into `SecurityFilterChain` with stateless session management.
  - `AuthService` handling registration with password strength regex validation, BCrypt hashing (strength 12), login, refresh token rotation, and Redis revocation tracking.
  - `UserService` managing profile updates and language/engine/mode preferences.
  - `AuthController` (`/api/v1/auth/register`, `/login`, `/refresh`, `/logout`) and `UserController` (`/api/v1/users/me`, `/me/preferences`).
  - Full MockMvc integration test suite covering registration, duplicate rejection, weak password rejection, authentication, refresh token rotation, and profile access control (19/19 tests passing).
  - Angular 17 Frontend Authentication UI:
    - Angular Material Login and Registration components with reactive forms and error handling.
    - Authenticated Dashboard with app toolbar, user menu, and quick action cards.
    - User Profile & Preferences editor for configuring default language, processing mode (local/cloud), and ASR engine/model.
    - Functional `authInterceptor` with automatic token attachment and 401 refresh token retry logic.
    - `authGuard` and `guestGuard` route protection with lazy loading.
- **Phase 4**:
  - AWS S3 / MinIO object storage service (`S3StorageService`) supporting direct uploads, chunked multipart uploads, pre-signed URL generation, and deletion.
  - Video entities (`Video`, `UploadSession`) and JPA repositories with owner-level isolation.
  - Deep file verification (`VideoValidator`) enforcing allowed extensions, 10 GB size limits, 240-minute duration limit, and binary magic bytes inspection.
  - Metadata extraction (`MetadataExtractionService`) using FFprobe JSON inspection with resilient fallback.
  - REST endpoints (`VideoController`): direct multipart upload, chunked upload initiation, chunk parts upload, multipart completion, paginated video listing, video details with streaming URL, and video deletion.
  - Integration test suite (`VideoControllerIntegrationTest`) covering all upload paths, format/duration/signature rejections, and access control (28/28 tests passing).
  - Angular 17 Frontend Video UI:
    - Interactive drag-and-drop file upload zone with live upload progress percentage and validation feedback (`VideoUploadComponent`).
    - Responsive video library with status filters, search, specs display, and pagination (`VideoLibraryComponent`).
    - Video detail page with HTML5 `<video>` preview player, technical metadata breakdown, and transcription CTA (`VideoDetailComponent`).
    - Configured lazy routes protected by `authGuard`.
- **Phase 5**:
  - Python 3.12 AI Worker environment configured with virtual environment (`.venv`) and pip dependencies.
  - Modular worker architecture created (`consumers`, `processors`, `providers`, `diarization`, `translation`, `summarization`, `subtitles`, `storage`, `messaging`, `models`, `config`, `health`).
  - Centralized Pydantic configuration (`WorkerSettings`) and structured correlation logging (`setup_logging`).
  - Full message schemas for job requests (`TranscriptionJobRequest`), progress events (`ProcessingEvent`), and results (`TranscriptionJobResult`).
  - Auto-reconnecting RabbitMQ client (`RabbitMQClient`) with topology declarations for exchanges, queues, and DLQ.
  - Persistent event and result publishing (`EventPublisher`).
  - Multi-stage pipeline processing lifecycle (`BaseJobProcessor`, `DefaultJobProcessor`).
  - Distributed idempotency management (`IdempotencyManager`) backed by Redis to prevent duplicate compute runs.
  - Exponential backoff retry handler (`RetryHandler`) with max retry threshold (3 retries) and dead-letter routing to `transcription.dlq`.
  - RabbitMQ worker consumer (`TranscriptionConsumer`) with manual message acknowledgments (`basic_ack`, `basic_nack`).
  - FastAPI health check & metrics server with `/health` liveness and `/health/detailed` readiness endpoints.
  - Complete pytest test suite (19/19 tests passing).
- **Phase 6**:
  - Unified ASR provider abstraction (`ASRProvider`) with schemas for `WordTimestamp`, `TranscriptionSegment`, and `TranscriptionOutput`.
  - `faster-whisper` integration powered by CTranslate2 with model selection (`tiny` to `large-v3`) and `MockASRProvider` for deterministic testing.
  - `DeviceDetector` probing CUDA acceleration, VRAM allocation, and precision selection (`float16`, `int8_float16`) with automatic `int8` CPU fallback.
  - `ASRProviderFactory` instantiating optimal providers based on runtime telemetry.
  - `AudioExtractor` extracting 16kHz mono 16-bit PCM WAV audio using FFmpeg with resilient fallback.
  - `VADChunker` chunking speech up to 10 minutes (600s) at silence boundaries.
  - `VideoTranscriptionProcessor` executing end-to-end multi-stage pipeline with global timeline offset calculation and progress event dispatching.
  - `ASRBenchmarkSuite` profiling 10m, 30m, 1h, and 4h audio workloads with Real-Time Factor (RTF) and memory telemetry.
  - Full test suite passing (30/30 pytest tests passing).
