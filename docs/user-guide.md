# VideoToText AI — End-User & Operator Guide

Welcome to **VideoToText AI**, an enterprise-grade platform that converts video media into structured, searchable, diarized, translated, and exportable transcripts with AI-driven executive insights.

---

## 1. Getting Started

### 1.1 Account Registration & Authentication
1. Navigate to the web application URL (e.g., `https://videototext.ai` or `http://localhost:4200`).
2. Click **Register** to create a new user profile with your email and a strong password (minimum 8 characters, containing uppercase, lowercase, numbers, and special symbols).
3. Upon registration, log in using your credentials.
4. The system secures sessions using stateless JWT access tokens (15-minute lifespan) and automatic 7-day refresh token rotation stored in secure HTTP headers.

### 1.2 User Preferences & Default Settings
From the top navigation bar, click on your profile icon and select **Preferences**:
- **Default ASR Model**: Select your preferred Whisper model (`tiny`, `base`, `small`, `medium`, `large-v3`).
- **Processing Mode**: Choose `Cloud GPU` for maximum speed or `Local / Self-Hosted` for air-gapped environments.
- **Default Translation Language**: Set your primary target language (`Hindi [HI]`, `Marathi [MR]`, `Spanish [ES]`, `French [FR]`, `German [DE]`).

---

## 2. Video Upload & Ingestion

### 2.1 Supported Formats & Constraints
- **Supported Video Containers**: MP4, MKV, MOV, AVI, WEBM, FLV, M4V, TS.
- **Maximum File Size**: Up to 10 GB.
- **Maximum Duration**: 240 minutes (4 hours).
- **Security Validation**: All uploaded files undergo deep magic bytes inspection to reject disguised executables or corrupted streams.

### 2.2 Uploading Videos
1. Click **Upload Video** on the dashboard.
2. Drag and drop your video file into the designated upload area or click **Browse Files**.
3. For videos larger than 100 MB, the system automatically uses **Chunked Multipart Uploading**, allowing resume capability in case of network interruptions.
4. Once uploaded, technical metadata (duration, resolution, audio channels, bitrate) is extracted automatically via FFprobe.

---

## 3. Transcription & Processing Pipeline

### 3.1 Initiating Transcription
From your **Video Library**, select any uploaded video and click **Transcribe**:
- **ASR Engine Configuration**: Select the model size and compute quantization.
- **Speaker Diarization**: Check **Enable Diarization** to identify distinct speakers.
- **Multilingual Translation**: Select one or more target translation languages.
- **AI Summary**: Check **Generate Executive Insights** to produce chapter breakdowns and bullet summaries.

### 3.2 Real-Time Processing Stepper
Once queued, the platform transitions to the live monitoring screen via Server-Sent Events (SSE):
1. **Upload & Validation** (0% - 10%)
2. **Audio Extraction** (10% - 25%)
3. **VAD Chunking** (25% - 35%)
4. **ASR Inference** (35% - 70%)
5. **Alignment & Diarization** (70% - 85%)
6. **Translation & Summary** (85% - 95%)
7. **Finalization** (95% - 100%)

---

## 4. Interactive Transcript Editor

### 4.1 Synchronized Playback & Text Tracking
- Click any word or sentence in the transcript to instantly seek the video player to that exact timestamp.
- As the video plays, the transcript automatically auto-scrolls and highlights the currently spoken sentence in real time.

### 4.2 In-Place Editing & Timestamps
1. Click the **Edit** icon next to any transcript segment.
2. Modify the text, correct misheard terminology, or adjust start/end boundaries.
3. Click **Save** to persist your changes. Every edit is tracked in the immutable **Audit Revision History** modal.

### 4.3 Speaker Labeling & Filtering
- Reassign speakers using the inline dropdown (e.g., change `SPEAKER_00` to `Dr. Sharma`).
- Use the **Speaker Filter Bar** to isolate dialogue spoken by a single participant.

---

## 5. Multilingual Translation

1. Switch between language tabs in the editor:
   - **Original [EN]**
   - **हिन्दी [HI]**
   - **मराठी [MR]**
   - **Español [ES]**, **Français [FR]**, **Deutsch [DE]**
2. On-demand translation is available if a target language was not pre-selected during initial transcription.
3. All translated segments preserve exact timestamp synchronization with the video player.

---

## 6. AI Summary & Key Insights

Click on the **Executive Insights** tab to view:
- **Executive Overview**: High-level abstract of the meeting or lecture.
- **Key Takeaways & Action Items**: Bulleted action points with speaker attribution and time references.
- **Interactive Chapter Markers**: Click any chapter header to jump to that topical section in the video.

---

## 7. Subtitle Generation & Multi-Format Exports

Click **Export Subtitles** from the action menu to download subtitles in your desired format:
- **SubRip (.srt)**: Standard subtitles compatible with YouTube, VLC, and video editors.
- **WebVTT (.vtt)**: Web video text tracks with speaker voice styling.
- **Plain Text (.txt)**: Clean transcript document for documentation and search indexing.
- **Structured JSON (.json)**: Complete programmatic export containing word-level timestamps, speaker centroids, and translation layers.

---

## 8. Support & Troubleshooting

- **File upload fails**: Ensure the video does not exceed 10 GB or 240 minutes, and that the file is not corrupted.
- **Diarization shows only one speaker**: Check that participants speak clearly with minimal background noise and distinct audio volume.
- **Further assistance**: Inspect the [Deployment Runbook](file:///C:/Data/Projects/VideoToText_AI-springboot-angular/docs/deployment-runbook.md) or open an issue on GitHub.
