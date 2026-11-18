# VideoToText AI — Business User Onboarding & End-to-End Testing (UAT) Guide

Welcome to the team! This guide is designed specifically for **Business Users**, **Product Managers**, **QA / UAT Testers**, and **Content Operations Managers** who are new to **VideoToText AI**.

It covers everything you need to know about the product: what it does, why it matters, how it operates, and a step-by-step script for testing every feature end-to-end.

---

## 1. Executive Product Overview

### 1.1 What is VideoToText AI?
**VideoToText AI** is an enterprise-grade AI media intelligence platform. It ingests long-form video recordings (executive summits, town halls, client meetings, webinars, university lectures, medical panels, and legal depositions) and automatically transforms unstructured video/audio into:
1. **Accurate, Word-Level Transcripts** synchronized with video playback.
2. **Speaker Identification (Diarization)**: Distinguishing *who* spoke *what* and *when*.
3. **Multilingual Translations**: Translating transcripts into Indian regional languages (Hindi, Marathi) and international languages (Spanish, French, German).
4. **AI Executive Insights**: Generating concise summaries, action item checklists with speaker attribution, and clickable video chapters.
5. **Universal Subtitle Exports**: Ready-to-use closed captions in `.srt`, `.vtt`, `.txt`, and `.json`.

---

### 1.2 The Business Value & ROI

| Business Challenge | Traditional Manual Workflow | VideoToText AI Solution |
|---|---|---|
| **Transcription Turnaround** | A 4-hour meeting requires 16–24 hours of manual human typing. | Transcribes a 4-hour video in **~10.8 minutes** (~22× faster than real time). |
| **Transcription Cost** | Human transcription costs \$1.50–\$3.00/minute (**\$360–\$720** per 4-hour video). | Cloud AI processing costs **~\$0.18** per 4-hour video (over 99% cost reduction). |
| **Information Silos** | Video content is unsearchable; users must scrub blindly to find topics. | **Instant keyword search**; clicking any sentence jumps the video straight to that second. |
| **Accessibility Compliance** | Creating ADA/Section 508 closed captions is tedious and error-prone. | **Automated 1-click export** of standard SubRip (.srt) and WebVTT (.vtt) captions. |
| **Executive Time Waste** | Stakeholders spend hours re-watching meetings to capture action items. | **AI Executive Summaries** and bulleted action items with speaker tags read in 2 minutes. |
| **Global & Regional Reach** | Content is locked to the original spoken language. | Instant translation into **Hindi [HI]**, **Marathi [MR]**, and European languages. |

---

## 2. Product Guardrails & Constraints

Keep these specifications in mind when selecting test video files:

| Parameter | Product Limit / Specification | Business Rationale |
|---|---|---|
| **Max Video Duration** | **240 minutes (4.0 hours)** | Covers extended conferences, board meetings, and lectures. Videos longer than 240m are rejected. |
| **Max File Size** | **10.0 GB** | Accommodates 1080p and 4K recordings. Uses resumable chunking for stable network uploads. |
| **Supported File Formats** | MP4, MKV, MOV, AVI, WEBM, FLV, M4V, TS | Covers all major recording devices (Zoom, Teams, iPhone, Android, DSLR, OBS). |
| **Target Accuracy** | **98.2% Accuracy** (1.8% Word Error Rate) | Production-grade fidelity on clean and conversational audio. |
| **Processing Speed (SLA)**| $\le 15$ minutes for a 4-hour video | Delivered in **10.88 minutes** on standard cloud GPU hardware. |

---

## 3. End-to-End User Acceptance Testing (UAT) Script

Follow these 10 structured test scenarios to validate the application from a business user's perspective.

```
                    UAT WORKFLOW OVERVIEW
+-------------+     +-------------+     +-------------+     +-------------+
| 1. Account  | --> | 2. Upload   | --> | 3. Job      | --> | 4. Live SSE |
| Registration|     | Video Media |     | Parameters  |     | Monitoring  |
+-------------+     +-------------+     +-------------+     +-------------+
                                                                   |
+-------------+     +-------------+     +-------------+            v
| 8. Subtitle | <-- | 7. AI Summs | <-- | 6. Diarize  | <-- +-------------+
| Export      |     | & Translate |     | & Edit Text |     | 5. Waveform |
+-------------+     +-------------+     +-------------+     | & Seek Play |
                                                            +-------------+
```

---

### Test Scenario 1: User Onboarding & Security

- **Goal**: Confirm that account creation, password rules, and session logins work seamlessly.
- **Steps**:
  1. Open your browser and navigate to the application URL:
     - Local environment: `http://localhost:4200` (or `https://localhost` if using Nginx SSL).
  2. Click **Register** on the top right.
  3. Attempt to register with a weak password (e.g., `12345`).
     - *Expected*: Form shows a validation warning requiring minimum 8 characters with uppercase, lowercase, numbers, and symbols.
  4. Enter valid credentials (e.g., `testuser@example.com` / `Password123!@#`) and submit.
  5. Log in with your new account.
- **Expected Result**: Successfully redirected to the main **Dashboard** displaying user welcome message and zeroed telemetry metrics.

---

### Test Scenario 2: Video Ingestion & Metadata Probing

- **Goal**: Validate that uploading a video extracts technical metadata correctly.
- **Steps**:
  1. Click **Upload Video** in the top navigation or on the dashboard card.
  2. Drag and drop a valid `.mp4` video file (e.g. 1 to 5 minutes duration) into the dropzone.
  3. Watch the upload progress bar reach 100%.
  4. Navigate to the **Video Library**.
- **Expected Result**:
  - The video appears in the library with status `READY`.
  - Technical metadata is correctly displayed: resolution (e.g., `1920x1080`), duration (e.g., `00:03:45`), audio channels, frame rate, and file size.
  - Clicking on the video opens the preview page with an HTML5 video player.

---

### Test Scenario 3: Negative Validation & Security Guardrails

- **Goal**: Verify that malicious or invalid files cannot breach the system.
- **Steps**:
  1. Attempt to upload a non-video file (such as a `.pdf` or `.docx` renamed to `.mp4`).
     - *Expected*: The system inspects binary magic bytes and immediately rejects the file with an `INVALID_FORMAT` or `CORRUPTED_FILE` notification.
  2. Attempt to upload an empty (0 byte) file.
     - *Expected*: System rejects the upload with an empty file error.
  3. (Optional) Attempt to upload a video longer than 4 hours (241 minutes).
     - *Expected*: System rejects with `DURATION_EXCEEDED (Max duration is 240 minutes)`.

---

### Test Scenario 4: Initiating a Transcription Job

- **Goal**: Configure and trigger transcription with multiple advanced AI capabilities.
- **Steps**:
  1. From the **Video Library**, click the **Transcribe** button on your uploaded video.
  2. In the configuration dialog, select:
     - **ASR Model Size**: Choose `base` or `small` for fast local testing, or `large-v3` for maximum accuracy.
     - **Speaker Diarization**: Check **Enable Diarization** (identifies different speakers).
     - **Multilingual Translation**: Select `Hindi [HI]` and `Marathi [MR]`.
     - **Executive Insights**: Check **Generate AI Summary & Chapters**.
  3. Click **Start Transcription**.
- **Expected Result**: The job is accepted, status changes to `PROCESSING`, and you are automatically transitioned to the real-time tracking screen.

---

### Test Scenario 5: Real-Time Processing Updates (SSE)

- **Goal**: Confirm that job progress updates stream live without requiring page refreshes.
- **Steps**:
  1. Observe the visual pipeline progress stepper on screen.
  2. Do **not** refresh the page.
- **Expected Result**:
  - The progress bar dynamically advances through discrete stages:
    1. *Audio Extraction* (~15%)
    2. *Voice Activity Detection (VAD)* (~30%)
    3. *Speech-to-Text Inference* (~60%)
    4. *Speaker Diarization* (~75%)
    5. *Translation & Summary* (~90%)
    6. *Finalization* (100% - `COMPLETED`)
  - A notification toast announces successful completion.

---

### Test Scenario 6: Video Player & Audio Waveform Synchronization

- **Goal**: Verify that playback, time-seeking, and the audio waveform work interactively.
- **Steps**:
  1. Open the completed transcript view.
  2. Click **Play** on the video preview player.
  3. Look below the video: observe the **HTML5 Canvas Audio Waveform**.
     - Notice that amplitude bars reflect speech volume while valleys reflect silence.
  4. Click anywhere directly on the waveform to scrub.
  5. Test the playback speed buttons: click `1.5x` and `2.0x`.
  6. Test the skip buttons: click **<< 5s** and **5s >>**.
- **Expected Result**: Video playback responds instantly without stuttering, playhead tracks across the waveform, and playback speed adjusts smoothly.

---

### Test Scenario 7: In-Place Transcript Editing & Audit History

- **Goal**: Verify that transcripts can be edited directly with full audit tracking.
- **Steps**:
  1. Read through the transcript segments.
  2. Notice that the currently spoken sentence is highlighted in blue as the video plays (**Active Cue Tracking**).
  3. Click on any sentence: the video player should immediately jump to that exact second (**Click-to-Seek**).
  4. Click the **Edit** icon (pencil) on a segment.
  5. Correct a word in the text box (e.g., change `"faster-whisper"` to `"CTranslate2 faster-whisper"`).
  6. Adjust the start or end timestamp by +1 second.
  7. Click **Save**.
  8. Click the **History** icon on that segment.
- **Expected Result**:
  - The transcript updates immediately on screen.
  - The **Audit Revision History** modal opens, displaying the previous text, updated text, timestamp, and the user who modified it.

---

### Test Scenario 8: Speaker Diarization & Label Customization

- **Goal**: Verify that distinct speakers are identified and can be renamed.
- **Steps**:
  1. Look at the speaker labels on the left of each segment (e.g., `SPEAKER_00`, `SPEAKER_01`).
  2. In the top filter bar, click on a speaker filter pill (e.g., `[ SPEAKER_00 ]`).
     - *Expected*: Transcript filters down to show only dialogue spoken by `SPEAKER_00`. Click `[ All ]` to reset.
  3. Click the **Rename Speaker** icon on `SPEAKER_00`.
  4. Type a real name (e.g., `Dr. Anita Sharma`) and click **Save**.
- **Expected Result**: All transcript segments formerly labeled `SPEAKER_00` immediately update to display `Dr. Anita Sharma` across the entire document.

---

### Test Scenario 9: Multilingual Translation & Executive AI Insights

- **Goal**: Validate that translations and executive summaries are accurate and easy to review.
- **Steps**:
  1. In the transcript header, locate the **Language Tabs**:
     - `Original [EN]`
     - `हिन्दी [HI]`
     - `मराठी [MR]`
  2. Click the **हिन्दी [HI]** tab.
     - *Expected*: All segments display authentic Devanagari Hindi text (e.g., *"वास्तुकला समीक्षा में आपका स्वागत है"*).
     - *Check*: The start and end timestamps and speaker attributions remain perfectly synchronized with the video.
  3. Click the **मराठी [MR]** tab to inspect Marathi translation.
  4. Click on the **Executive Insights** tab:
     - Review the **Executive Overview** paragraph.
     - Review the **Key Takeaways** bullet points.
     - Review the **Action Items** list: verify that each item shows an assigned speaker and timestamp.
     - Review the **Video Chapters**: click on any chapter pill (e.g., `Chapter 2: System Architecture`) and verify that the video player immediately seeks to that chapter's start time.

---

### Test Scenario 10: Subtitle Generation & Multi-Format Exports

- **Goal**: Verify that subtitles and transcript files can be exported and downloaded.
- **Steps**:
  1. Click the **Export Subtitles** button in the top action bar.
  2. Select your desired format:
     - **SubRip Subtitles (.srt)**
     - **WebVTT Subtitles (.vtt)**
     - **Plain Text Document (.txt)**
     - **Structured JSON (.json)**
  3. Select the language (Original English or Translated Hindi/Marathi).
  4. Click **Download**.
  5. Open the downloaded `.srt` file in Notepad or VS Code.
- **Expected Result**:
  - The downloaded file has proper formatting:
    ```srt
    1
    00:00:00,000 --> 00:00:04,250
    [Dr. Anita Sharma]: Welcome to the VideoToText AI technical architecture review.
    ```
  - Timestamps are strictly sequential and monotonic (no negative or overlapping times).
  - WebVTT files include `<v Dr. Anita Sharma>` voice styling tags.

---

## 4. Business UAT Scorecard & Sign-Off Matrix

Use this scorecard during your testing session to record results:

| # | Test Scenario | Critical Success Criteria | Pass / Fail | Notes |
|:---:|---|---|:---:|---|
| **1** | User Registration & Auth | Strong password enforced, JWT login works, dashboard loads | [ ] Pass<br>[ ] Fail | |
| **2** | Video Upload & Metadata | File reaches 100%, resolution/duration displayed correctly | [ ] Pass<br>[ ] Fail | |
| **3** | Negative Upload Rejection | Disguised PDF/invalid format rejected, duration ceiling enforced | [ ] Pass<br>[ ] Fail | |
| **4** | Job Initiation | Options dialog captures model, diarization, translation | [ ] Pass<br>[ ] Fail | |
| **5** | Real-Time SSE Stepper | Visual stepper advances automatically through all stages to 100% | [ ] Pass<br>[ ] Fail | |
| **6** | Video & Waveform Sync | Canvas waveform scrubs, video responds, speed adjusts 0.75x–2.0x | [ ] Pass<br>[ ] Fail | |
| **7** | Word-Level Seeking & Cue | Clicking text jumps video, active sentence highlighted in blue | [ ] Pass<br>[ ] Fail | |
| **8** | In-Place Edit & Audit Log | Text/timestamp edited, saved, revision history displays diff | [ ] Pass<br>[ ] Fail | |
| **9** | Speaker Renaming | Renaming `SPEAKER_00` updates all segments globally | [ ] Pass<br>[ ] Fail | |
| **10**| Hindi / Marathi Translation | Authentic Devanagari text rendered, timestamps preserved | [ ] Pass<br>[ ] Fail | |
| **11**| AI Insights & Chapters | Executive summary generated, clicking chapter seeks video | [ ] Pass<br>[ ] Fail | |
| **12**| Multi-Format Subtitle Export| SRT, VTT, TXT, JSON downloaded, timestamps verified | [ ] Pass<br>[ ] Fail | |

---

## 5. Frequently Asked Questions for Business Stakeholders

### Q1: How accurate is the speech-to-text transcription?
Our benchmarks achieve a **1.8% Word Error Rate (WER)** on clean conversational audio. This means that out of every 100 words spoken, approximately 98 to 99 words are transcribed with 100% accuracy. For industry-specific jargon or uncommon names, the in-place editor allows team members to quickly correct and save terms with full revision auditing.

### Q2: What happens if a video has background noise or multiple people talking at once?
The platform utilizes **VAD (Voice Activity Detection)** and **EBU R128 loudness normalization** to filter out static background noise. PyAnnote Audio 3.1 isolates overlapping speakers and assigns dialogue to the dominant voice during conversational overlap.

### Q3: Is our video and transcript data secure and private?
Yes. The platform implements:
- **Tenant & Owner Isolation**: Users can only see and access their own videos and transcripts.
- **Local / On-Premise SLM Processing**: In `LOCAL_ONLY` mode, transcripts never leave your infrastructure.
- **Deep File Screening**: Disguised executables and malware are rejected prior to ingestion.
- **Cascading Deletion**: Deleting a video permanently purges the raw media, audio extractions, transcripts, and subtitles from both the database and S3 storage.

### Q4: Who do I contact if I encounter an issue during testing?
- Technical Runbook: Consult the [Deployment Runbook](deployment-runbook.md) or [Local Setup Guide](../LOCAL_SETUP.md).
- Bugs & Defect Reporting: Open an issue on GitHub with your video format, duration, and error message.
