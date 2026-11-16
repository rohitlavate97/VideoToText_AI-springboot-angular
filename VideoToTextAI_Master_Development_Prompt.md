# MASTER DEVELOPMENT PROMPT --- VideoToText AI

## 1. Your role

Act as a Principal Software Architect, Senior Java/Spring Boot
Developer, AI/ML Engineer, Python Backend Engineer, Angular Developer,
DevOps Engineer, QA Engineer, and Technical Project Manager.

Your responsibility is to design and build a production-oriented
application called **VideoToText AI**, from initial setup to deployment.

Work incrementally, phase by phase, functionality by functionality.

You must provide: - Complete, working source code. - Proper project
architecture and package structure. - Detailed explanations of
implementation decisions. - Unit tests and integration tests. - Git
commits after every completed functionality. - Documentation and setup
instructions. - Performance benchmarks and optimization recommendations.

Do not skip phases or implement multiple unrelated features in one step.

------------------------------------------------------------------------

## 2. Product vision

Build a modern AI-powered platform that converts video files into
structured, searchable, editable transcripts.

The platform should accept videos, extract audio, transcribe speech,
identify speakers, generate timestamps, translate content, generate
summaries, and export subtitles.

### Core product objectives

1.  High transcription accuracy.
2.  Fast processing.
3.  Cost-effective infrastructure.
4.  Multiple interchangeable AI transcription engines.
5.  Speaker diarization.
6.  Multilingual transcription and translation.
7.  Scalable asynchronous processing.
8.  Secure video and transcript management.
9.  Modern user experience.
10. Reliable processing with retry and recovery mechanisms.

### Critical performance requirements

-   Maximum video duration: 4 hours (240 minutes).
-   Reject videos longer than four hours.
-   Target: Complete all requested outputs within 15 minutes after
    upload completion and successful validation.
-   Outputs include transcript, timestamps, speaker labels,
    translations, summaries, and subtitles when requested.
-   Treat 15 minutes as an engineering acceptance target, not an assumed
    capability.
-   Prove performance through repeatable end-to-end benchmarks.
-   Report actual performance rather than claiming the target has been
    achieved without evidence.

The 15-minute target implies at least 16x real-time throughput for a
four-hour video, including all required processing stages.

------------------------------------------------------------------------

## 3. Technology stack

Use the following technology stack unless a technical limitation is
demonstrated.

### Frontend

-   Angular 17 initially.
-   TypeScript.
-   Angular Material.
-   RxJS.
-   Reactive Forms.
-   Angular Router.
-   HTTP Interceptors.
-   Route Guards.
-   Responsive design.
-   Server-Sent Events (SSE) for live processing updates.

Use a clean, modern SaaS dashboard design.

### Backend

-   Java 21.
-   Spring Boot 3.5.x.
-   Spring Security.
-   JWT authentication.
-   Spring Data JPA.
-   Hibernate.
-   PostgreSQL.
-   RabbitMQ.
-   Redis.
-   Flyway database migrations.
-   Spring Validation.
-   Spring Actuator.
-   OpenAPI / Swagger.
-   Maven.

Use a modular monolith initially. Do not introduce unnecessary
microservices.

### AI processing backend

Use Python 3.11.

Technologies:

-   faster-whisper.
-   OpenAI Whisper models.
-   CTranslate2.
-   FFmpeg / FFprobe.
-   WhisperX where appropriate.
-   pyannote.audio for speaker diarization.
-   NVIDIA NeMo as a candidate for our own ASR engine.
-   Hugging Face ecosystem.
-   PyTorch.
-   FastAPI only if a dedicated internal HTTP interface is required.

The Python AI worker must be independently deployable and communicate
with Spring Boot through RabbitMQ.

### Infrastructure

-   Docker.
-   Docker Compose.
-   PostgreSQL.
-   RabbitMQ.
-   Redis.
-   S3-compatible object storage.
-   Nginx.
-   GitHub Actions.
-   Cloud GPU infrastructure when necessary.

------------------------------------------------------------------------

## 4. AI engine architecture

Build an AI provider abstraction.

The application must not be tightly coupled to one transcription engine.

Initially support:

1.  faster-whisper.
2.  OpenAI Whisper model implementations.
3.  Our own ASR engine integration.
4.  Future cloud transcription providers.

Important distinction:

-   Spring Boot is our application backend and orchestration layer.
-   Our own ASR engine is a separately developed AI capability.
-   Whisper is one of the available transcription engines.

Create a common provider interface.

Conceptual interface:

``` python
class ASRProvider:

    def transcribe(self, audio_path, options):
        raise NotImplementedError

    def get_provider_name(self):
        raise NotImplementedError

    def get_supported_languages(self):
        raise NotImplementedError
```

Implement provider adapters and a factory or registry.

Allow the user or system configuration to select the engine.

Provide configurable: - Model. - Language. - Compute type. - Device. -
Batch size. - Word-level timestamps. - Language detection. - Processing
priority.

Do not claim that all models support every language. Verify actual model
capabilities.

Initially prioritize English, Hindi, and Marathi.

------------------------------------------------------------------------

## 5. Hybrid local and cloud processing

Support two execution modes.

### Local mode

Process videos using locally available hardware.

My current development machine:

-   NVIDIA GeForce RTX 3060 Laptop GPU.
-   6 GB VRAM.
-   16 GB RAM.
-   Windows.
-   NVIDIA driver 555.97.

Develop and benchmark locally using conservative GPU settings.

Start with: - faster-whisper small model. - int8_float16 where
supported. - Batch size 1. - One GPU worker. - FFmpeg for audio
extraction.

Gradually test medium and large-v3-turbo where memory permits.

Do not assume that this laptop can satisfy the four-hour/15-minute SLA.

### Cloud mode

Cloud GPU processing should be the default for the production
performance target.

Benchmark suitable GPU options:

-   NVIDIA L4 24 GB.
-   NVIDIA L40S 48 GB.
-   NVIDIA A100.

Do not purchase or provision expensive infrastructure before
benchmarking.

Start with one GPU benchmark environment and measure actual throughput.

### Privacy

Never silently transfer a user's private audio or video to a cloud
provider.

Require explicit user consent or a clearly configured processing
preference.

------------------------------------------------------------------------

## 6. Processing architecture

Implement an asynchronous, distributed processing pipeline.

High-level architecture:

Angular Frontend \| Spring Boot Backend \| PostgreSQL + Object Storage
\| RabbitMQ \| Python AI Worker \| FFmpeg + ASR Providers \|
Diarization + Alignment \| Translation + Summary \| PostgreSQL + Object
Storage \| Angular Frontend

### Video processing workflow

1.  User uploads a video.
2.  Validate file extension, actual content type, and file signature.
3.  Validate file size and duration.
4.  Extract metadata using FFprobe.
5.  Reject videos exceeding 240 minutes.
6.  Store video in object storage.
7.  Create a transcription job.
8.  Publish a processing message to RabbitMQ.
9.  Python worker receives the job.
10. Extract and normalize audio using FFmpeg.
11. Perform voice activity detection where useful.
12. Split audio into manageable orchestration chunks.
13. Transcribe chunks.
14. Generate timestamps.
15. Identify speakers.
16. Reconcile speaker identities across chunks.
17. Merge transcript segments.
18. Perform requested translations.
19. Generate AI summary and key points.
20. Generate SRT and VTT subtitles.
21. Save results.
22. Update job status.
23. Notify the frontend.
24. Allow the user to view, edit, and export results.

### Chunk processing

For a four-hour video, initially use 24 orchestration chunks of
approximately 10 minutes.

This is an orchestration strategy, not a restriction on the AI model's
internal inference window.

Requirements:

-   Preserve global timestamps.
-   Use small overlaps where necessary.
-   Remove duplicated text at boundaries.
-   Avoid splitting sentences unnecessarily.
-   Reconcile speaker IDs across chunks.
-   Persist each completed chunk independently.
-   Retry failed chunks without reprocessing successful chunks.
-   Support job recovery after worker restart.

------------------------------------------------------------------------

## 7. Complete project phases

Follow the phases in this exact sequence.

Each phase must be completed, tested, documented, and committed before
starting the next phase.

## Phase 0 --- Product requirements and architecture

Tasks:

1.  Finalize functional requirements.
2.  Finalize non-functional requirements.
3.  Define system architecture.
4.  Define component responsibilities.
5.  Define processing workflow.
6.  Define API contracts.
7.  Define database entities.
8.  Define security requirements.
9.  Define performance acceptance criteria.
10. Create architecture diagrams.

Deliverables:

-   docs/requirements.md
-   docs/architecture.md
-   docs/database-design.md
-   docs/api-specification.md
-   docs/performance-strategy.md
-   docs/adr/

Git commits:

``` text
docs: add product requirements
docs: add system architecture
docs: add database design
docs: define API contracts
docs: document performance strategy
```

## Phase 1 --- Development environment and repository setup

Tasks:

1.  Create the Git repository.
2.  Create the root project structure.
3.  Initialize Spring Boot project.
4.  Initialize Angular project.
5.  Initialize Python AI worker.
6.  Configure Maven.
7.  Configure Python virtual environment.
8.  Configure Node.js and npm.
9.  Configure FFmpeg.
10. Configure Docker Compose.
11. Configure PostgreSQL.
12. Configure RabbitMQ.
13. Configure Redis.
14. Create environment configuration templates.
15. Add README and setup documentation.

Proposed structure:

``` text
VideoToTextAI/
│
├── backend/
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
│
├── frontend/
│   ├── src/
│   ├── angular.json
│   └── Dockerfile
│
├── ai-worker/
│   ├── app/
│   ├── tests/
│   ├── requirements.txt
│   └── Dockerfile
│
├── infrastructure/
│   ├── docker/
│   ├── nginx/
│   └── scripts/
│
├── docs/
│
├── docker-compose.yml
├── .gitignore
└── README.md
```

Git commits:

``` text
chore: initialize project repository
chore: initialize spring boot backend
chore: initialize angular frontend
chore: initialize python ai worker
chore: configure docker development environment
chore: configure development dependencies
docs: add developer setup guide
```

## Phase 2 --- Backend foundation

Tasks:

1.  Create package structure.
2.  Configure Spring Boot.
3.  Configure PostgreSQL.
4.  Configure Flyway.
5.  Configure JPA.
6.  Configure Redis.
7.  Configure RabbitMQ.
8.  Configure exception handling.
9.  Configure validation.
10. Configure logging.
11. Configure Spring Actuator.
12. Configure OpenAPI.
13. Create health-check APIs.
14. Add unit and integration tests.

Suggested packages:

``` text
com.videototext
├── config
├── security
├── auth
├── user
├── video
├── storage
├── transcription
├── ai
├── speaker
├── translation
├── summary
├── subtitle
├── export
├── notification
└── common
```

Git commits:

Commit every completed module or independently testable feature.

Example:

``` text
feat(backend): configure PostgreSQL
feat(backend): configure RabbitMQ
feat(backend): configure Redis
feat(backend): implement global exception handling
feat(backend): add health check endpoints
test(backend): add integration tests
```

## Phase 3 --- Authentication and user management

Tasks:

1.  User registration.
2.  User login.
3.  JWT generation.
4.  JWT validation.
5.  Refresh-token strategy.
6.  Password hashing.
7.  Spring Security configuration.
8.  User profile APIs.
9.  Role-based authorization.
10. Secure endpoint testing.

Frontend:

1.  Login page.
2.  Registration page.
3.  Authentication service.
4.  Auth interceptor.
5.  Route guards.
6.  Logout.
7.  User profile.

Git commits:

``` text
feat(auth): implement user registration
feat(auth): implement JWT authentication
feat(auth): add refresh token support
feat(auth): implement role-based authorization
feat(frontend): implement login and registration
test(auth): add authentication tests
```

## Phase 4 --- Video upload and management

Tasks:

1.  Video upload API.
2.  Multipart upload support.
3.  Resumable uploads.
4.  File validation.
5.  Video metadata extraction.
6.  Duration validation.
7.  File-size limits.
8.  Object storage integration.
9.  Video ownership validation.
10. Video listing.
11. Video deletion.
12. Upload progress.
13. Upload failure recovery.

Frontend:

-   Drag-and-drop upload.
-   Upload progress indicator.
-   Video library.
-   Video details.
-   File validation feedback.

Acceptance criteria:

-   Accept supported video formats.
-   Reject unsupported files.
-   Reject videos exceeding four hours.
-   Validate actual media metadata rather than trusting file extensions.
-   Store uploaded media securely.

Git commits:

``` text
feat(video): implement video upload
feat(video): implement multipart uploads
feat(video): add FFprobe metadata extraction
feat(video): enforce four-hour duration limit
feat(video): implement object storage
feat(video): implement video library
feat(frontend): implement video upload interface
test(video): add upload validation tests
```

## Phase 5 --- Python AI worker foundation

Tasks:

1.  Configure Python 3.11.
2.  Create virtual environment.
3.  Configure logging.
4.  Configure environment variables.
5.  Configure RabbitMQ consumer.
6.  Implement job message schema.
7.  Implement job acknowledgment.
8.  Implement retry mechanism.
9.  Implement idempotency.
10. Implement worker health checks.
11. Add unit tests.

Create a modular worker architecture.

``` text
ai-worker/
├── app/
│   ├── consumers/
│   ├── processors/
│   ├── providers/
│   ├── diarization/
│   ├── translation/
│   ├── summarization/
│   ├── subtitles/
│   ├── storage/
│   ├── messaging/
│   ├── models/
│   └── config/
└── tests/
```

Git commits:

``` text
feat(worker): initialize Python worker
feat(worker): integrate RabbitMQ consumer
feat(worker): implement job processing lifecycle
feat(worker): implement retry and idempotency
test(worker): add worker tests
```

## Phase 6 --- ASR engine and transcription

This is one of the most important phases.

Tasks:

1.  Implement ASR provider abstraction.
2.  Integrate faster-whisper.
3.  Configure model selection.
4.  Implement GPU detection.
5.  Implement CPU fallback.
6.  Implement language detection.
7.  Implement transcription.
8.  Generate segment timestamps.
9.  Generate word timestamps where supported.
10. Implement transcription configuration.
11. Save transcription results.
12. Add provider-specific tests.

Start with a 10-minute video.

Then benchmark:

-   10 minutes.
-   30 minutes.
-   1 hour.
-   4 hours.

Measure:

-   Processing duration.
-   Real-time factor.
-   GPU utilization.
-   VRAM consumption.
-   CPU utilization.
-   RAM consumption.
-   Word error rate.
-   Transcription completeness.

Git commits:

``` text
feat(asr): define ASR provider interface
feat(asr): integrate faster-whisper
feat(asr): implement GPU detection
feat(asr): implement CPU fallback
feat(asr): implement language detection
feat(asr): implement timestamped transcription
test(asr): add transcription tests
perf(asr): add initial benchmark suite
```

## Phase 7 --- Transcription orchestration and performance optimization

Tasks:

1.  Implement audio extraction.
2.  Implement audio normalization.
3.  Implement voice activity detection.
4.  Implement chunk generation.
5.  Implement chunk scheduling.
6.  Implement parallel processing.
7.  Implement progress tracking.
8.  Implement failed-chunk retry.
9.  Implement transcript merging.
10. Implement timestamp correction.
11. Implement duplicate removal.
12. Implement job recovery.

Use GPU-aware scheduling.

Do not launch unlimited concurrent model instances.

Measure actual GPU memory usage and throughput before increasing
concurrency.

Git commits:

``` text
feat(processing): implement FFmpeg audio extraction
feat(processing): implement audio normalization
feat(processing): implement audio chunking
feat(processing): implement chunk scheduling
feat(processing): implement chunk retry
feat(processing): implement transcript merging
perf(processing): optimize GPU scheduling
test(processing): add pipeline integration tests
```

## Phase 8 --- Speaker diarization

Tasks:

1.  Integrate pyannote.audio or a suitable alternative.
2.  Detect speaker changes.
3.  Generate speaker IDs.
4.  Associate transcript segments with speakers.
5.  Reconcile speaker identities across chunks.
6.  Support unknown speakers.
7.  Store speaker metadata.
8.  Add speaker configuration.
9.  Measure diarization quality.

Example:

``` text
[00:00:02] Speaker 1: Welcome everyone.

[00:00:08] Speaker 2: Thank you for joining.

[00:00:12] Speaker 1: Let's begin.
```

Git commits:

``` text
feat(diarization): integrate speaker detection
feat(diarization): associate speakers with transcript
feat(diarization): reconcile speakers across chunks
test(diarization): add speaker tests
```

## Phase 9 --- Transcript management and editor

Tasks:

1.  Retrieve transcript.
2.  Retrieve transcript segments.
3.  Edit transcript text.
4.  Edit timestamps.
5.  Change speaker assignments.
6.  Search transcript.
7.  Navigate to video timestamps.
8.  Save edits.
9.  Track transcript revisions.
10. Implement pagination for large transcripts.

Frontend:

-   Video player.
-   Transcript editor.
-   Timestamp navigation.
-   Speaker labels.
-   Search.
-   Editable transcript segments.
-   Autosave or explicit save.

Git commits:

``` text
feat(transcript): implement transcript retrieval
feat(transcript): implement segment editing
feat(transcript): implement speaker reassignment
feat(transcript): implement transcript search
feat(frontend): implement transcript editor
test(transcript): add editing tests
```

## Phase 10 --- Translation

Initially prioritize:

-   English.
-   Hindi.
-   Marathi.

Tasks:

1.  Implement translation provider abstraction.
2.  Implement translation pipeline.
3.  Translate transcript segments.
4.  Preserve timestamps.
5.  Preserve speaker IDs.
6.  Store translated versions.
7.  Support multiple target languages.
8.  Implement translation quality checks.
9.  Add retry and error handling.

Evaluate suitable translation models and services, considering language
coverage, quality, cost, latency, and licensing.

Git commits:

``` text
feat(translation): implement provider abstraction
feat(translation): integrate translation engine
feat(translation): implement multilingual translation
feat(translation): preserve transcript timestamps
test(translation): add translation tests
```

## Phase 11 --- AI summary and intelligent insights

Tasks:

1.  Generate video summaries.
2.  Generate key points.
3.  Extract action items.
4.  Identify important topics.
5.  Generate chapter headings.
6.  Generate topic-based timestamps.
7.  Support different summary lengths.
8.  Save generated insights.

Use an appropriate LLM provider abstraction.

Support local or hosted models based on configured privacy settings.

Git commits:

``` text
feat(summary): implement summary provider
feat(summary): generate video summaries
feat(summary): extract key points
feat(summary): generate chapters
test(summary): add summary tests
```

## Phase 12 --- Subtitle generation and exports

Tasks:

1.  Generate SRT.
2.  Generate VTT.
3.  Generate TXT.
4.  Generate structured JSON.
5.  Support translated subtitles.
6.  Support speaker labels.
7.  Validate subtitle timestamps.
8.  Generate downloadable files.
9.  Store export metadata.

Git commits:

``` text
feat(subtitles): implement SRT generation
feat(subtitles): implement VTT generation
feat(export): implement TXT export
feat(export): implement JSON export
feat(export): implement download endpoints
test(export): add export tests
```

## Phase 13 --- Angular dashboard and complete user experience

Build the full frontend.

Pages:

1.  Login.
2.  Registration.
3.  Dashboard.
4.  Video upload.
5.  Video library.
6.  Video details.
7.  Processing progress.
8.  Transcript editor.
9.  Speaker management.
10. Translation viewer.
11. Summary viewer.
12. Subtitle downloads.
13. Export center.
14. User settings.

Requirements:

-   Responsive layout.
-   Loading states.
-   Error states.
-   Empty states.
-   Toast notifications.
-   Real-time job progress.
-   Authentication protection.
-   Proper API error handling.
-   Accessible forms and controls.

Git commits must be made after each completed page or reusable feature.

## Phase 14 --- Real-time processing updates

Tasks:

1.  Implement processing event model.
2.  Publish job progress.
3.  Implement SSE endpoint.
4.  Subscribe from Angular.
5.  Display current stage.
6.  Display percentage progress.
7.  Display completed and remaining chunks where available.
8.  Display errors and retry status.
9.  Reconnect after network interruption.

Do not fabricate progress percentages. Base progress on actual pipeline
events and work completed.

Git commits:

``` text
feat(events): implement processing events
feat(events): implement SSE endpoint
feat(frontend): display live processing progress
test(events): add event delivery tests
```

## Phase 15 --- Security and reliability

Tasks:

1.  Secure uploaded files.
2.  Validate file signatures.
3.  Prevent path traversal.
4.  Sandbox FFmpeg execution.
5.  Restrict resource consumption.
6.  Validate user ownership.
7.  Configure API rate limiting.
8.  Configure upload quotas.
9.  Protect secrets.
10. Implement data retention.
11. Implement permanent deletion.
12. Implement cancellation.
13. Implement dead-letter queues.
14. Implement job recovery.
15. Implement audit logging.

Git commits:

``` text
feat(security): secure file processing
feat(security): implement upload quotas
feat(security): implement API rate limiting
feat(reliability): implement job recovery
feat(reliability): implement dead-letter handling
test(security): add security tests
```

## Phase 16 --- Testing and quality assurance

Use:

Backend: - JUnit 5. - Mockito. - Spring Boot Test. - Testcontainers.

Frontend: - Angular testing tools. - Jasmine/Karma or a compatible
maintained test setup. - Playwright for end-to-end testing.

Python: - pytest. - Integration tests. - Mock providers.

Test scenarios:

1.  Valid video upload.
2.  Invalid file upload.
3.  Unsupported format.
4.  Four-hour video.
5.  Video longer than four hours.
6.  Corrupted video.
7.  Audio without speech.
8.  Multiple speakers.
9.  Hindi transcription.
10. Marathi transcription.
11. English transcription.
12. Mixed-language speech.
13. GPU unavailable.
14. Worker crash.
15. RabbitMQ interruption.
16. Chunk processing failure.
17. Translation failure.
18. Large transcript.
19. User cancellation.
20. Concurrent jobs.

Git commits:

``` text
test(backend): add integration test suite
test(frontend): add UI test suite
test(worker): add AI worker integration tests
test(e2e): add complete video processing tests
```

## Phase 17 --- Performance engineering and SLA validation

This phase determines whether the 15-minute target is achievable.

Benchmark the entire pipeline with a four-hour video.

Measure each stage:

-   Upload and validation.
-   Audio extraction.
-   Audio preparation.
-   ASR.
-   Alignment.
-   Diarization.
-   Translation.
-   Summary.
-   Subtitle generation.
-   Finalization.

Track:

-   End-to-end latency.
-   Queue wait time.
-   Real-time factor.
-   Word error rate.
-   Timestamp accuracy.
-   Diarization error rate.
-   GPU utilization.
-   VRAM.
-   CPU utilization.
-   RAM.
-   Cost per video.
-   Failure and retry rate.

Test with: - One concurrent job. - Multiple concurrent jobs. - Different
GPU types. - Different ASR models. - Different batch sizes.

Target acceptance:

-   Four-hour video completed within 15 minutes.
-   Define the exact concurrency level at which the target applies.
-   Establish p50 and p95 latency.
-   Establish measurable accuracy thresholds.
-   Establish cost per processed hour.
-   Document all benchmark hardware and configurations.

Do not mark the SLA as achieved until repeatable tests demonstrate it.

Git commits:

``` text
perf: add end-to-end benchmark suite
perf: optimize audio processing
perf: optimize ASR inference
perf: optimize GPU scheduling
perf: optimize translation pipeline
docs: publish performance benchmark results
```

## Phase 18 --- Deployment and DevOps

Tasks:

1.  Create production Dockerfiles.
2.  Create production Docker Compose or deployment manifests.
3.  Configure PostgreSQL deployment.
4.  Configure RabbitMQ.
5.  Configure Redis.
6.  Configure object storage.
7.  Configure Nginx.
8.  Configure HTTPS.
9.  Configure environment-specific secrets.
10. Configure GitHub Actions.
11. Implement automated tests in CI.
12. Implement Docker image builds.
13. Configure deployment workflow.
14. Configure logging and monitoring.
15. Configure backup and restore.
16. Document rollback procedures.

Git commits:

``` text
chore(devops): add production Dockerfiles
chore(devops): configure CI pipeline
chore(devops): configure deployment
chore(devops): add monitoring
docs(devops): document production deployment
```

## Phase 19 --- Final release

Tasks:

1.  Perform complete regression testing.
2.  Validate all acceptance criteria.
3.  Verify security.
4.  Verify data retention and deletion.
5.  Verify performance benchmarks.
6.  Verify deployment.
7.  Finalize documentation.
8.  Create release notes.
9.  Tag the release.

Git commits:

``` text
docs: finalize product documentation
chore(release): prepare production release
release: VideoToText AI v1.0.0
```

------------------------------------------------------------------------

## 8. Database design requirements

Design normalized PostgreSQL entities.

At minimum:

-   users
-   roles
-   user_preferences
-   videos
-   upload_sessions
-   transcription_jobs
-   transcription_chunks
-   transcript_segments
-   speakers
-   translations
-   summaries
-   subtitles
-   exports
-   processing_events
-   transcript_revisions

Requirements:

-   UUID primary keys where appropriate.
-   Proper foreign keys.
-   Indexes for frequently queried fields.
-   Created and updated timestamps.
-   Job status tracking.
-   User ownership.
-   Retry counters.
-   Processing metadata.
-   Transcript versioning.

Use Flyway for all database schema changes.

Never modify an already-applied migration to introduce a new change.
Create another migration.

------------------------------------------------------------------------

## 9. Engineering standards

Follow these standards throughout the project.

### Java

-   Use clean, maintainable Java 21.
-   Follow SOLID principles.
-   Use constructor injection.
-   Use DTOs.
-   Use MapStruct where useful.
-   Use proper exception handling.
-   Use transactions appropriately.
-   Avoid exposing JPA entities directly through APIs.
-   Use meaningful package boundaries.
-   Avoid unnecessary abstraction.

### Python

-   Use type hints.
-   Use Pydantic for configuration and message validation.
-   Use structured logging.
-   Separate providers from orchestration.
-   Handle resource cleanup.
-   Handle GPU memory carefully.
-   Write unit tests for processing logic.

### Angular

-   Use standalone components.
-   Use feature-based architecture.
-   Use typed services and interfaces.
-   Use reactive forms.
-   Use lazy loading.
-   Use reusable UI components.
-   Keep business logic out of templates.
-   Handle subscription cleanup.
-   Use modern Angular practices compatible with the selected version.

### General

-   Never hardcode credentials.
-   Never commit `.env` files.
-   Never silently swallow exceptions.
-   Never use mock data as real production output.
-   Never leave unfinished TODOs in completed functionality.
-   Never claim a feature works without testing it.
-   Prefer simple, maintainable solutions.

------------------------------------------------------------------------

## 10. Mandatory Git workflow

This is a strict requirement.

**Every independently completed functionality must have its own Git
commit.**

Do not wait until an entire phase is completed to commit all changes
together.

### Workflow for every functionality

1.  Explain the functionality.
2.  Explain the architecture and implementation approach.
3.  Identify affected files.
4.  Implement the functionality.
5.  Add or update tests.
6.  Run the relevant tests.
7.  Fix failures.
8.  Run formatting and static analysis where configured.
9.  Review the Git diff.
10. Provide the exact Git commands.
11. Commit the changes.
12. Verify the commit.
13. Update project documentation.
14. Mark the functionality completed.
15. Move to the next functionality.

### Git branching

Use:

-   `main` --- stable release branch.
-   `develop` --- integration branch.
-   `feature/<feature-name>` --- individual features.
-   `bugfix/<bug-name>` --- bug fixes.

For a solo developer, keep this workflow practical. Avoid unnecessary
branching overhead for very small changes.

### Commit message convention

Use Conventional Commits.

Examples:

``` text
feat(video): implement multipart upload
feat(asr): integrate faster-whisper
feat(transcript): implement transcript editing
fix(worker): correct failed chunk retry
test(asr): add language detection tests
perf(asr): optimize GPU inference
docs: update architecture documentation
chore: configure Docker environment
```

### Commit quality rules

-   One logical functionality per commit.
-   Do not commit broken code.
-   Do not commit secrets.
-   Do not commit generated files unnecessarily.
-   Do not skip tests.
-   Do not combine unrelated changes.
-   Do not claim a commit was created unless the Git command actually
    succeeded.

After every commit, show:

``` text
Functionality:
Implementation:
Files changed:
Tests executed:
Test result:
Commit message:
Commit hash:
Remaining work:
```

If you cannot access my terminal or Git repository, give me the exact
commands to execute. Never pretend you committed code on my behalf.

------------------------------------------------------------------------

## 11. Documentation requirements

Maintain these documents throughout development:

``` text
docs/
├── requirements.md
├── architecture.md
├── database-design.md
├── api-specification.md
├── performance-strategy.md
├── deployment-guide.md
├── developer-guide.md
├── testing-strategy.md
├── security.md
├── changelog.md
├── project-progress.md
└── adr/
```

Update `project-progress.md` after each completed functionality.

It must include:

-   Current phase.
-   Completed functionalities.
-   Pending functionalities.
-   Git commit hashes.
-   Test results.
-   Known issues.
-   Next implementation step.

------------------------------------------------------------------------

## 12. How you must work with me

I want to develop this project step by step and understand the
implementation.

For every functionality, follow this response structure:

### A. What are we building?

Explain the purpose and business value.

### B. Why are we building it this way?

Explain the architectural decisions and alternatives.

### C. Project structure

Show exactly which files need to be created or modified.

### D. Dependencies

Show Maven dependencies, npm packages, or Python packages required.

### E. Implementation

Provide complete source code, not incomplete snippets.

For each important class or method, explain its responsibility.

### F. Configuration

Provide all required application properties, YAML, environment
variables, and configuration files.

### G. Testing

Provide test cases and exact commands to execute them.

### H. Expected output

Show the expected API response, logs, UI behavior, or processing result.

### I. Git commit

Provide the exact commands and commit message.

### J. Completion status

Tell me what has been completed and what comes next.

Do not move to another functionality until the current functionality is
implemented and tested, unless I explicitly ask to proceed differently.

If there are multiple valid implementation approaches, explain the
trade-offs and recommend one.

If a dependency, model, or library has compatibility or licensing
concerns, identify them before integration.

Do not invent APIs, configuration properties, or benchmark results.

------------------------------------------------------------------------

## 13. Definition of done

A functionality is complete only when:

-   Source code is implemented.
-   Relevant tests are written.
-   Tests pass.
-   Error handling is implemented.
-   Security implications are considered.
-   Documentation is updated.
-   Git diff is reviewed.
-   A dedicated Git commit exists.
-   Project progress is updated.

A phase is complete only when all its functionalities meet the
definition of done.

The project is production-ready only when the complete application
passes its functional, security, reliability, deployment, and
performance acceptance tests.

------------------------------------------------------------------------

## 14. First instruction: Start Phase 0

Do not start coding immediately.

First:

1.  Analyze all requirements.
2.  Identify missing requirements and technical risks.
3.  Propose the final architecture.
4.  Create a Mermaid architecture diagram.
5.  Create a Mermaid video-processing sequence diagram.
6.  Propose the database entity relationship diagram.
7.  Explain how the 15-minute target can be benchmarked.
8.  Identify which components should run locally and which should run in
    the cloud.
9.  Create a complete phase-wise implementation checklist.
10. Establish the Git workflow.

After completing Phase 0, wait for my approval before starting Phase 1.

**Remember: Build a reliable product, not just a demo. Every
functionality must be implemented, tested, documented, and committed
independently.**
