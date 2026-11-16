# API Specification — VideoToText AI

**Base URL**: `/api/v1`

**Authentication**: All endpoints require a valid JWT `Authorization: Bearer <token>` header unless marked as **Public**.

**Standard Response Envelope**:
```json
{
  "success": true,
  "data": { },
  "message": "Operation completed successfully",
  "timestamp": "2026-01-01T00:00:00Z"
}
```

**Standard Error Response**:
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Human-readable message",
    "details": [ ]
  },
  "timestamp": "2026-01-01T00:00:00Z"
}
```

---

## 1. Authentication APIs

### POST `/auth/register` — Public

Register a new user account.

**Request**:
```json
{
  "email": "user@example.com",
  "password": "SecurePass123!",
  "firstName": "Rohit",
  "lastName": "Sharma"
}
```

**Response** `201 Created`:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "email": "user@example.com",
    "firstName": "Rohit",
    "lastName": "Sharma"
  }
}
```

**Validation**:
- Email: valid format, unique.
- Password: min 8 chars, uppercase, lowercase, digit, special char.
- Names: 1–100 chars, not blank.

---

### POST `/auth/login` — Public

Authenticate and receive tokens.

**Request**:
```json
{
  "email": "user@example.com",
  "password": "SecurePass123!"
}
```

**Response** `200 OK`:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbG...",
    "refreshToken": "eyJhbG...",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

---

### POST `/auth/refresh` — Public (requires valid refresh token)

Rotate the refresh token and issue a new access token.

**Request**:
```json
{
  "refreshToken": "eyJhbG..."
}
```

**Response** `200 OK`: Same shape as login.

---

### POST `/auth/logout`

Invalidate the current refresh token.

**Request**:
```json
{
  "refreshToken": "eyJhbG..."
}
```

**Response** `200 OK`:
```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

---

## 2. User APIs

### GET `/users/me`

Get the current user's profile.

**Response** `200 OK`:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "email": "user@example.com",
    "firstName": "Rohit",
    "lastName": "Sharma",
    "roles": ["ROLE_USER"],
    "preferences": {
      "defaultLanguage": "en",
      "processingMode": "local",
      "preferredAsrEngine": "faster-whisper",
      "preferredAsrModel": "small"
    },
    "createdAt": "2026-01-01T00:00:00Z"
  }
}
```

### PUT `/users/me`

Update profile fields.

### PUT `/users/me/preferences`

Update user preferences (language, processing mode, ASR settings).

---

## 3. Video APIs

### POST `/videos/upload`

Upload a video file (multipart/form-data).

**Request**: `multipart/form-data`
- `file` (required): Video file.
- `title` (optional): Display title.

**Response** `201 Created`:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "originalFilename": "meeting.mp4",
    "fileSize": 524288000,
    "durationSeconds": 3600,
    "codec": "h264",
    "resolution": "1920x1080",
    "contentType": "video/mp4",
    "status": "UPLOADED",
    "createdAt": "2026-01-01T00:00:00Z"
  }
}
```

**Errors**:
- `400` — Invalid file type, corrupted file, missing file.
- `413` — File exceeds size limit.
- `422` — Video duration exceeds 240 minutes.

---

### POST `/videos/upload/initiate`

Initiate a multipart/resumable upload session.

**Request**:
```json
{
  "filename": "long_meeting.mp4",
  "fileSize": 5368709120,
  "contentType": "video/mp4",
  "totalParts": 50
}
```

**Response** `200 OK`:
```json
{
  "success": true,
  "data": {
    "uploadSessionId": "uuid",
    "partSize": 107374182,
    "totalParts": 50
  }
}
```

### PUT `/videos/upload/{sessionId}/parts/{partNumber}`

Upload a single part.

### POST `/videos/upload/{sessionId}/complete`

Finalize a multipart upload and trigger validation.

---

### GET `/videos`

List the current user's videos with pagination.

**Query params**: `page` (default 0), `size` (default 20), `sort` (default `createdAt,desc`), `status`.

**Response** `200 OK`:
```json
{
  "success": true,
  "data": {
    "content": [ { "id": "uuid", "originalFilename": "...", "status": "..." } ],
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3
  }
}
```

### GET `/videos/{id}`

Get video details. Returns `403` if the user does not own the video.

### DELETE `/videos/{id}`

Delete a video and all associated data (cascading). Returns `204 No Content`.

---

## 4. Transcription APIs

### POST `/transcriptions`

Create a new transcription job for a video.

**Request**:
```json
{
  "videoId": "uuid",
  "asrEngine": "faster-whisper",
  "asrModel": "small",
  "sourceLanguage": "auto",
  "computeType": "int8_float16",
  "batchSize": 1,
  "wordTimestamps": true,
  "targetLanguages": ["hi", "mr"],
  "generateSummary": true,
  "generateSubtitles": true,
  "processingMode": "local",
  "priority": 5
}
```

**Response** `202 Accepted`:
```json
{
  "success": true,
  "data": {
    "jobId": "uuid",
    "status": "QUEUED",
    "videoId": "uuid",
    "createdAt": "2026-01-01T00:00:00Z"
  },
  "message": "Transcription job queued"
}
```

---

### GET `/transcriptions/{jobId}`

Get job details including status, progress, and configuration.

**Response** `200 OK`:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "videoId": "uuid",
    "status": "TRANSCRIBING",
    "currentStage": "ASR",
    "progressPercent": 45.8,
    "totalChunks": 24,
    "completedChunks": 11,
    "failedChunks": 0,
    "asrEngine": "faster-whisper",
    "asrModel": "small",
    "sourceLanguage": "en",
    "startedAt": "2026-01-01T00:05:00Z",
    "processingDurationMs": 120000,
    "createdAt": "2026-01-01T00:04:55Z"
  }
}
```

---

### GET `/transcriptions/{jobId}/segments`

Get transcript segments with pagination.

**Query params**: `page`, `size`, `speakerId`, `searchQuery`, `startTime`, `endTime`.

**Response** `200 OK`:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "segmentIndex": 0,
        "startTime": 2.34,
        "endTime": 5.67,
        "text": "Welcome everyone to today's meeting.",
        "speaker": {
          "id": "uuid",
          "label": "Speaker 1",
          "displayName": "Rohit"
        },
        "confidence": 0.95,
        "language": "en"
      }
    ],
    "page": 0,
    "size": 50,
    "totalElements": 5200,
    "totalPages": 104
  }
}
```

---

### PUT `/transcriptions/{jobId}/segments/{segmentId}`

Edit a transcript segment (text, timestamps, speaker assignment).

**Request**:
```json
{
  "text": "Welcome everyone to today's meeting.",
  "startTime": 2.34,
  "endTime": 5.80,
  "speakerId": "uuid"
}
```

---

### GET `/transcriptions/{jobId}/segments/search`

Full-text search across transcript segments.

**Query params**: `q` (search query), `page`, `size`.

---

### POST `/transcriptions/{jobId}/cancel`

Cancel a running transcription job. Returns `200 OK`.

---

### GET `/transcriptions`

List all transcription jobs for the current user. Supports pagination and status filter.

---

## 5. Speaker APIs

### GET `/transcriptions/{jobId}/speakers`

List all speakers identified in a transcription.

**Response** `200 OK`:
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "label": "Speaker 1",
      "displayName": null,
      "totalSpeakingTimeMs": 125000,
      "segmentCount": 47
    }
  ]
}
```

### PUT `/transcriptions/{jobId}/speakers/{speakerId}`

Update speaker display name.

**Request**:
```json
{
  "displayName": "Rohit Sharma"
}
```

---

## 6. Translation APIs

### GET `/transcriptions/{jobId}/translations`

List available translations for a job.

### GET `/transcriptions/{jobId}/translations/{language}`

Get translated transcript segments for a target language.

### POST `/transcriptions/{jobId}/translations`

Request a new translation (if not already requested at job creation).

**Request**:
```json
{
  "targetLanguage": "hi"
}
```

---

## 7. Summary APIs

### GET `/transcriptions/{jobId}/summary`

Get the AI-generated summary.

**Response** `200 OK`:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "summary": "This meeting covered three main topics...",
    "keyPoints": [
      "Decided to launch Phase 1 by Q2",
      "Budget approved for infrastructure"
    ],
    "actionItems": [
      "Rohit to prepare the deployment plan by Friday"
    ],
    "topics": ["Project Timeline", "Budget", "Infrastructure"],
    "chapters": [
      { "title": "Introduction", "startTime": 0.0, "endTime": 120.5 },
      { "title": "Project Timeline Discussion", "startTime": 120.5, "endTime": 1800.0 }
    ],
    "llmProvider": "local",
    "createdAt": "2026-01-01T00:15:00Z"
  }
}
```

---

## 8. Export & Subtitle APIs

### GET `/transcriptions/{jobId}/subtitles`

List available subtitle files.

### GET `/transcriptions/{jobId}/subtitles/download`

Download a subtitle file.

**Query params**: `format` (`srt`, `vtt`), `language` (default: source), `includeSpeakers` (default: false).

**Response**: File download (`application/octet-stream`).

---

### GET `/transcriptions/{jobId}/export`

Download an export file.

**Query params**: `format` (`txt`, `json`, `srt`, `vtt`), `language`.

**Response**: File download.

---

## 9. Event APIs (SSE)

### GET `/events/jobs/{jobId}` — SSE Stream

Subscribe to real-time processing updates for a job.

**Response**: `text/event-stream`

```
event: progress
data: {"jobId":"uuid","stage":"TRANSCRIBING","progressPercent":45.8,"completedChunks":11,"totalChunks":24,"message":"Transcribing chunk 12 of 24"}

event: progress
data: {"jobId":"uuid","stage":"TRANSCRIBING","progressPercent":50.0,"completedChunks":12,"totalChunks":24,"message":"Transcribing chunk 13 of 24"}

event: completed
data: {"jobId":"uuid","stage":"COMPLETED","progressPercent":100.0,"message":"Transcription completed successfully"}
```

**Event types**: `queued`, `progress`, `stage_change`, `error`, `retry`, `completed`, `failed`, `cancelled`.

---

## 10. Health & System APIs

### GET `/actuator/health` — Public

Spring Boot Actuator health check.

### GET `/actuator/info` — Public

Application info.

### GET `/system/status` — Admin only

System status including queue depths, worker status, storage usage.

---

## 11. Error Codes

| Code | HTTP Status | Description |
|------|------------|-------------|
| `VALIDATION_ERROR` | 400 | Request validation failed |
| `AUTHENTICATION_REQUIRED` | 401 | Missing or invalid JWT |
| `ACCESS_DENIED` | 403 | Insufficient permissions |
| `RESOURCE_NOT_FOUND` | 404 | Requested resource not found |
| `DUPLICATE_RESOURCE` | 409 | Resource already exists (e.g., email) |
| `FILE_TOO_LARGE` | 413 | File exceeds size limit |
| `UNSUPPORTED_FORMAT` | 415 | Unsupported file type |
| `DURATION_EXCEEDED` | 422 | Video duration exceeds 240 minutes |
| `RATE_LIMIT_EXCEEDED` | 429 | Too many requests |
| `PROCESSING_ERROR` | 500 | Internal processing failure |
| `SERVICE_UNAVAILABLE` | 503 | Downstream service unavailable |
