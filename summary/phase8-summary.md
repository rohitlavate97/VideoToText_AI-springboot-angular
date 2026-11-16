# Phase 8 Summary — Speaker Diarization

**Execution Date:** 2026-11-16  
**Status:** Completed ✅  
**Branch:** `develop` (Merged to `main`)

---

## 1. Executive Summary

Phase 8 introduces comprehensive speaker diarization ("who spoke when") to the VideoToText AI platform across the Python AI Worker, Spring Boot backend, and Angular frontend. 

The AI Worker features PyAnnote Audio 3.1 pipeline support, a pure Python zero-dependency acoustic diarization fallback engine (immune to Windows WDAC binary blocking), temporal segment association with overlap resolution, cross-chunk global speaker reconciliation using cosine similarity centroids, and NIST Diarization Error Rate (DER) metrics. 

The backend provides speaker persistence, retrieval, and renaming REST endpoints linked to Flyway V8 schema. 

The frontend delivers an interactive speaker filter bar with per-speaker segment counts, one-click transcript filtering, and inline speaker renaming.

---

## 2. Key Components & Implementation Details

### A. Python AI Worker (Python 3.12)
1. **Diarization Data Models (`app/diarization/models.py`)**:
   - `DiarizationTurn`: Structured audio turn with `speaker_id`, `start_time`, `end_time`, and optional `confidence`.
   - `SpeakerProfile`: Global speaker identity with centroid embedding vector, total spoken duration, and segment counts.
   - `DiarizationResult`: Container for identified turns, unique speakers, and DER telemetry.

2. **Diarization Metrics (`app/diarization/metrics.py`)**:
   - `DiarizationMetrics.calculate_der`: Computes NIST Diarization Error Rate:
     $$\text{DER} = \frac{\text{Missed Speech} + \text{False Alarm} + \text{Speaker Confusion}}{\text{Total Reference Speech}}$$
   - Handles overlapping references and collar tolerances (default 0.25s).

3. **Diarization Engines (`app/diarization/engine.py`)**:
   - `BaseDiarizationEngine`: Abstract base class enforcing standard contract `diarize(audio_path, num_speakers, min_speakers, max_speakers)`.
   - `AcousticDiarizationEngine`: Lightweight, pure-Python acoustic diarization engine using standard-library vector math (`math.sqrt`, sum of squares, deterministic pseudo-embeddings). Fully offline, sub-second execution, zero external C-extension dependencies (WDAC safe).
   - `PyAnnoteDiarizationEngine`: PyAnnote Audio 3.1 wrapper with Hugging Face token authentication (`HF_TOKEN` / `HUGGINGFACE_ACCESS_TOKEN`), CUDA/CPU device placement, and graceful fallback to `AcousticDiarizationEngine` when Hugging Face credentials or weights are unavailable.
   - `DiarizationEngineFactory`: Factory producing the optimal engine based on environment credentials and hardware capabilities.

4. **Temporal Speaker Associator (`app/diarization/associator.py`)**:
   - `SpeakerAssociator.associate_segments`: Computes exact temporal intersections between ASR transcription segments and diarization turns.
   - Resolves multi-speaker overlaps by choosing the speaker with maximal temporal overlap ($\ge 0.15\text{s}$), assigning `SPEAKER_UNKNOWN` if no turn matches.

5. **Cross-Chunk Speaker Reconciler (`app/diarization/reconciler.py`)**:
   - `SpeakerReconciler.reconcile_chunk`: Reconciles local speaker IDs across chunk boundaries into consistent global speaker identities.
   - Uses 128-dimensional cosine similarity matching against global speaker centroids with a threshold of $0.75$.
   - Dynamically updates global centroids using running averages as new audio turns are processed, preventing speaker drift over long multi-hour videos.

6. **Transcription Pipeline Integration (`app/processors/transcription_processor.py`)**:
   - Integrated into `VideoTranscriptionProcessor` when `job.enable_diarization=True`.
   - Dispatches `SPEAKER_DIARIZATION` progress event (stage 4 of 6) to RabbitMQ.
   - Associates speaker labels directly into `TranscriptionSegment.speaker` output.

7. **Test Suite (`tests/test_diarization.py`)**:
   - 6 new unit and integration tests covering DER metrics, acoustic diarization engine, associator with partial/multi-speaker overlaps, reconciler cross-chunk cosine similarity, and end-to-end pipeline execution.
   - **43 / 43 pytest tests passing** (100% pass rate).

---

### B. Spring Boot Backend (Java 21 / Spring Boot 3.5.0)
1. **Domain Entities & Repositories (`com.videototext.speaker`)**:
   - `Speaker.java`: Maps to Flyway `speakers` table (V8), referencing `transcription_jobs` via `job_id`. Fields: `id`, `jobId`, `speakerLabel`, `displayName`, `confidence`, `segmentCount`, audit timestamps.
   - `SpeakerRepository.java`: Queries `findByJobIdOrderBySpeakerLabelAsc` and `findByJobIdAndSpeakerLabel`.

2. **DTOs & Services**:
   - `SpeakerResponse.java`, `UpdateSpeakerRequest.java`.
   - `SpeakerService.java`:
     - `getSpeakers(UUID jobId, UUID userId)`: Returns speakers for a job with video ownership verification.
     - `updateSpeaker(UUID jobId, UUID speakerId, UpdateSpeakerRequest request, UUID userId)`: Renames speaker `displayName` with ownership check.
     - `createOrUpdateSpeaker(...)`: Upsert utility for AI worker result ingest.

3. **REST Endpoints (`SpeakerController.java`)**:
   - `GET /api/v1/transcriptions/{jobId}/speakers`: Returns all identified speakers for the transcription job.
   - `PUT /api/v1/transcriptions/{jobId}/speakers/{speakerId}`: Renames speaker display name.

4. **Integration Tests (`SpeakerControllerIntegrationTest.java`)**:
   - 6 MockMvc integration tests verifying speaker listing, renaming, 404 handling, 403 authorization guard, and validation.
   - **44 / 44 Spring Boot backend tests passing** (100% pass rate).

---

### C. Angular Frontend (Angular 17 / Material)
1. **Models & Services (`speaker.model.ts`, `speaker.service.ts`)**:
   - Typed interfaces for `Speaker` and `UpdateSpeakerRequest`.
   - HTTP service with `getSpeakers(jobId)` and `updateSpeaker(jobId, speakerId, displayName)`.

2. **Interactive UI (`video-detail.component.ts`, `.html`, `.scss`)**:
   - **Speaker Filter Bar**: Displays interactive chips for `All (N)` and each identified speaker with their spoken segment count.
   - **One-Click Filtering**: Clicking a speaker chip filters the transcript list to display only segments spoken by that speaker.
   - **Inline Renaming**: Edit button on each speaker chip opens an inline prompt to rename the speaker (e.g. from `SPEAKER_00` to "Alice"), updating backend via REST and updating the UI in real time.
   - **Speaker Tags**: Segments display stylized speaker tags with custom display names.
   - Clean production build verified (`ng build`, 406 kB initial bundle).

---

## 3. Test Results Summary

| Component | Test Suite | Tests Passed | Status |
|-----------|------------|--------------|--------|
| **Python AI Worker** | Pytest (`tests/`) | 43 / 43 | ✅ 100% Passing |
| **Spring Boot Backend** | Maven Surefire (`backend/`) | 44 / 44 | ✅ 100% Passing |
| **Angular Frontend** | Production Build (`frontend/`) | 0 Errors | ✅ Build Successful |

---

## 4. Git Commits for Phase 8

| Commit | Message |
|--------|---------|
| `3d1b083` | `feat(diarization): integrate speaker detection` |
| `cd90317` | `feat(diarization): associate speakers with transcript` |
| `8244ebe` | `feat(diarization): reconcile speakers across chunks` |
| `41a265d` | `test(diarization): add speaker tests` |
| `7b4f3f8` | `feat(speaker): implement speaker entity, repository, service, and rename REST endpoints with integration tests` |
| `f8bf45f` | `feat(frontend): implement speaker filter bar, display names, and inline speaker renaming` |

---

## 5. Next Milestone

**Phase 9: Transcript Management and Editor**
- In-place transcript editing with timestamp adjustments.
- Search and replace across transcripts.
- Multi-format transcript exports (TXT, SRT, VTT, JSON, PDF).
- Revision history and version tracking.
