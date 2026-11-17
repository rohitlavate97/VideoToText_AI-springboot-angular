# Phase 12 Summary — Subtitle Generation & Exports

**Execution Date:** 2026-11-17  
**Status:** Completed ✅  
**Branch:** `develop`

---

## 1. Executive Summary

Phase 12 delivers a comprehensive, production-grade **Subtitle Generation and Multi-Format Export Engine** spanning the Python AI Worker, Spring Boot backend, and Angular 17 frontend.

The platform enables users to generate, stream, and download subtitles and transcripts in standard industry formats:
- **SubRip (.srt)**: Sequential numeric cue indexing, millisecond comma timestamps (`HH:MM:SS,mmm`), and speaker prefixes.
- **WebVTT (.vtt)**: `WEBVTT` header, millisecond period timestamps (`HH:MM:SS.mmm`), and standard voice tags (`<v Speaker Name>`).
- **Plain Text (.txt)**: Clean chronological transcripts with optional speaker attributions and time offsets.
- **Structured JSON (.json)**: Machine-readable payload with cue IDs, precise decimal offsets, duration, speaker attributions, and confidence metrics.

The architecture handles translated subtitles across supported languages, incorporates speaker diarization labels from Phase 8, enforces strict timestamp monotonic consistency ($start \le end$), persists generated artifacts into S3/MinIO with unique compound constraints, and tracks export metadata in PostgreSQL.

---

## 2. Key Components & Implementation Details

### A. Python AI Worker (`ai-worker/app/subtitles/`)
1. **Domain Models (`models.py`)**:
   - `SubtitleFormat`: Enum (`srt`, `vtt`, `txt`, `json`) with MIME type mapping and file extensions.
   - `SubtitleEntry`: Normalized cue representation with automatic non-negative timestamp clamping and camelCase alias compatibility (`startTime`, `endTime`, `speakerName`).
   - `SubtitleGenerationRequest`: Flexible request supporting heterogeneous segment inputs (`TranscriptionSegment`, `TranslatedSegment`, dicts), speaker display mappings, line wrap thresholds, and language preferences.
   - `SubtitleResult`: Output envelope encapsulating formatted content, cue counts, total duration, and MIME details.

2. **Formatting & Validation Engine (`formatters.py`)**:
   - `format_timestamp_srt`: Formats float seconds into `HH:MM:SS,mmm` with rollover math.
   - `format_timestamp_vtt`: Formats float seconds into `HH:MM:SS.mmm`.
   - `format_timestamp_txt`: Formats float seconds into `[HH:MM:SS]`.
   - `wrap_text`: Natural word-boundary line wrapping adhering to subtitle presentation standards (default 42 chars/line, max 2 lines).
   - `validate_and_normalize_entries`: Chronological sorting, negative start clamping, inverted end-time recovery ($end \ge start + 0.1s$), and sequential 1-based cue re-indexing.
   - `format_srt`, `format_vtt`, `format_txt`, `format_json`: Dedicated formatters with optional speaker display name attribution.

3. **Generator Orchestration (`generator.py`)**:
   - `SubtitleGenerator`: Converts heterogeneous segment collections, normalizes timestamps, resolves speaker aliases, and executes format generation.

4. **Pytest Test Suite (`tests/test_subtitles.py`)**:
   - 10 targeted test cases covering:
     - Timestamp calculations and roll-over boundaries (0s, fractions, 1m+, 1h+, negative clamping).
     - Chronological sorting, empty cue filtering, and inverted timing resolution.
     - SubRip (.srt) syntax and speaker prefix formatting.
     - WebVTT (.vtt) header compliance and `<v Speaker>` voice tags.
     - Plain text format with and without timestamps.
     - Structured JSON schema validation with decimal seconds and duration metrics.
     - Heterogeneous segment ingestion (`TranscriptionSegment`, `TranslatedSegment`, Python dicts).
     - Empty transcript boundary handling.
     - Enum and MIME type verification.
   - **73 / 73 Python tests passing** (100% pass rate).

---

### B. Spring Boot Backend (Java 21 / Spring Boot 3.5.0)
1. **Domain Entities & Repositories (`com.videototext.subtitle`, `com.videototext.export`)**:
   - `Subtitle.java`: Mapped to Flyway `subtitles` table (V11) with compound uniqueness on `(job_id, format, language, includes_speakers)`.
   - `Export.java`: Mapped to Flyway `exports` table (V12) for tracking download and export history with file size auditing.
   - `SubtitleRepository.java` & `ExportRepository.java`: Custom JPA queries with job-scoped indexing.

2. **DTO Contracts (`com.videototext.subtitle.dto`, `com.videototext.export.dto`)**:
   - `GenerateSubtitleRequest.java`: Strongly validated request contract (`format`, `language`, `includeSpeakers`).
   - `SubtitleResponse.java`: Response envelope containing subtitle metadata and generated content.
   - `ExportResponse.java`: Audit record of exported files and storage keys.

3. **Service Layer (`SubtitleService.java`)**:
   - Ownership verification against `job.getUser().getId()`, returning `ForbiddenException` for unauthorized callers.
   - Support for both source language transcripts and translated segments from `TranslationRepository`.
   - Resolves speaker UUIDs and labels to human-friendly display names from `SpeakerRepository`.
   - Integrated with `Optional<StorageService>` for S3/MinIO upload/download with on-the-fly generation fallback.
   - Audit logging of export operations in `exports` table.

4. **REST Endpoints (`SubtitleController.java`)**:
   - `GET /api/v1/transcriptions/{jobId}/subtitles`: List available subtitles.
   - `POST /api/v1/transcriptions/{jobId}/subtitles`: Generate subtitle in specified format and language.
   - `GET /api/v1/transcriptions/{jobId}/subtitles/download`: Direct download with query params (`format`, `language`, `includeSpeakers`).
   - `GET /api/v1/transcriptions/{jobId}/subtitles/{subtitleId}/download`: Direct download by subtitle ID.
   - `DELETE /api/v1/transcriptions/{jobId}/subtitles/{subtitleId}`: Delete subtitle and remove from storage.
   - `GET /api/v1/transcriptions/{jobId}/export`: Download export file with audit logging.
   - `GET /api/v1/transcriptions/{jobId}/exports`: List export history.

5. **Backend Integration Tests (`SubtitleControllerIntegrationTest.java`)**:
   - 10 comprehensive MockMvc integration tests:
     - `generateSubtitle_SRT_Default_Success` (201 Created, SRT cue verification).
     - `generateSubtitle_VTT_WithSpeakers_Success` (201 Created, WEBVTT and voice tag checks).
     - `getSubtitles_List_Success` (200 OK listing multiple subtitles).
     - `downloadSubtitle_ByQueryParams_SRT_Success` (200 OK, attachment header, Content-Type: `application/x-subrip`).
     - `downloadSubtitle_ById_Success` (200 OK by UUID).
     - `exportTranscript_TXT_Success` (200 OK, Content-Type: `text/plain`).
     - `exportTranscript_JSON_Success` (200 OK, Content-Type: `application/json`).
     - `getExports_List_Success` (200 OK verifying audit records).
     - `getSubtitles_UnauthorizedUser_Forbidden` (403 Forbidden for non-owner).
     - `deleteSubtitle_Success` (200 OK deletion).
   - **79 / 79 backend tests passing** (100% pass rate).

---

### C. Angular Frontend (Angular 17 / Material)
1. **Models & Services (`subtitle.model.ts`, `subtitle.service.ts`)**:
   - Strongly typed TypeScript interfaces mirroring backend contracts.
   - Reactive HTTP methods for fetching, generating, downloading (as binary `Blob`), and deleting subtitles.
   - `saveBlob()` utility for automated in-browser file download triggers.

2. **Interactive UI (`video-detail.component.ts`, `.html`, `.scss`)**:
   - **Quick Export Dropdown Menu**:
     - Added to video action bar with one-click exports for SubRip (.srt), WebVTT (.vtt), Plain Text (.txt), and Structured JSON (.json).
     - Automatically exports in the currently selected language view (source or translation).
   - **Subtitles & Exports Management Card**:
     - Format selector dropdown.
     - "Include Speakers" checkbox toggle.
     - "Generate & Download" button with live loading spinner.
     - Saved Subtitles list displaying format badge, language badge, diarized tag, timestamp, direct download button, and delete action.

---

## 3. Verification & Metrics

| Test Suite | Previous | Added | Total Passing | Pass Rate |
|------------|----------|-------|---------------|-----------|
| **Python Pytest** (`ai-worker/tests/`) | 63 | +10 | **73** | **100%** |
| **Spring Boot Maven** (`backend/src/test/`) | 69 | +10 | **79** | **100%** |
| **Angular Frontend Build** | Clean | — | **0 Errors, 0 Warnings** | **100%** |
