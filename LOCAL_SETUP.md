# VideoToText AI — Local Development Setup Guide

This guide walks you through setting up and running the entire **VideoToText AI** multi-service platform locally on your machine for development, debugging, and testing.

---

## 1. Quick Reference & Port Mapping

| Service / Component | Technology Stack | Local URL / Port | Credentials / Notes |
|---|---|---|---|
| **Web Frontend** | Angular 17.3 + Angular Material | `http://localhost:4200` | Hot reload enabled |
| **Backend REST API** | Spring Boot 3.5.0 (Java 21) | `http://localhost:8080` | Swagger UI: `/swagger-ui.html` |
| **Backend Health** | Spring Boot Actuator | `http://localhost:8080/actuator/health` | Status probe |
| **Python AI Worker** | Python 3.11 + faster-whisper | `http://localhost:8081/health` | FastAPI health probe |
| **PostgreSQL 16** | Relational Database | `localhost:5432` | User: `videototext`<br>Pass: `videototext_password`<br>DB: `videototext` |
| **RabbitMQ 3.13** | Message Broker & AMQP | `localhost:5672` (AMQP)<br>`http://localhost:15672` (UI) | User: `videototext`<br>Pass: `videototext_password` |
| **Redis 7** | Cache & Idempotency Locks | `localhost:6379` | Auth: `videototext_password` |
| **MinIO Storage** | S3-Compatible Object Store | `localhost:9000` (API)<br>`http://localhost:9001` (Console) | Access: `minioadmin`<br>Secret: `minioadmin` |

---

## 2. Prerequisites & Tooling

Ensure the following tools are installed on your host system:

### 2.1 Required Runtimes & Compilers
- **Java 21 JDK**: Eclipse Temurin, Amazon Corretto, or OpenJDK 21
  ```bash
  java -version
  ```
- **Apache Maven 3.9+**:
  ```bash
  mvn -version
  ```
- **Node.js 20 LTS** & **npm 10+**:
  ```bash
  node -v
  npm -v
  ```
- **Python 3.11** (or 3.12):
  ```bash
  python --version
  ```
- **FFmpeg & FFprobe**: Required for media extraction and metadata probing
  ```bash
  ffmpeg -version
  ffprobe -version
  ```
  *Installation tips:*
  - **Windows**: `winget install Gyan.FFmpeg` or `choco install ffmpeg`
  - **macOS**: `brew install ffmpeg`
  - **Linux (Ubuntu/Debian)**: `sudo apt-get update && sudo apt-get install -y ffmpeg libsndfile1`
- **Docker & Docker Compose**: Used to run backing infrastructure (PostgreSQL, RabbitMQ, Redis, MinIO)
  ```bash
  docker --version
  docker compose version
  ```
- **Git**:
  ```bash
  git --version
  ```

---

## 3. Step-by-Step Local Setup

### Step 1: Clone Repository & Setup Environment

```bash
# 1. Clone repository
git clone https://github.com/rohitlavate97/VideoToText_AI-springboot-angular.git
cd VideoToText_AI-springboot-angular

# 2. Copy development environment configuration
cp .env.example .env
```

---

### Step 2: Start Backing Services (Docker Compose)

Run the pre-configured backing services:

```bash
docker compose up -d
```

Verify that all 4 services are healthy:
```bash
docker compose ps
```

Expected containers running:
- `videototext-postgres` (Port 5432)
- `videototext-rabbitmq` (Ports 5672, 15672)
- `videototext-redis` (Port 6379)
- `videototext-minio` (Ports 9000, 9001)
- `videototext-minio-init` (Auto-creates the `videototext` bucket and exits with code 0)

> [!TIP]
> You can inspect the MinIO Web Console at `http://localhost:9001` (login: `minioadmin` / `minioadmin`) to confirm the `videototext` bucket exists.
> You can inspect the RabbitMQ Management UI at `http://localhost:15672` (login: `videototext` / `videototext_password`).

---

### Step 3: Run Spring Boot Backend (Java 21)

1. Open a new terminal and navigate to `backend/`:
   ```bash
   cd backend
   ```

2. Compile and launch the Spring Boot application:
   ```bash
   mvn spring-boot:run
   ```
   *(Or on Windows PowerShell: `.\mvnw.cmd spring-boot:run`)*

3. **What happens on startup:**
   - Connects to PostgreSQL, Redis, RabbitMQ, and MinIO.
   - Automatically executes Flyway database migrations **V1 through V15**, creating all 15 relational tables and seeding default user roles.
   - Sets up RabbitMQ exchange `videototext.exchange` and queue bindings (`transcription.jobs`, `transcription.dlq`).

4. **Verify Backend Health:**
   - Health Probe: `http://localhost:8080/actuator/health` (should return `{"status":"UP"}`)
   - System Diagnostics: `http://localhost:8080/api/v1/health/system`
   - Interactive Swagger UI: `http://localhost:8080/swagger-ui.html`

---

### Step 4: Run Python AI Worker (Python 3.11)

1. Open a new terminal and navigate to `ai-worker/`:
   ```bash
   cd ai-worker
   ```

2. Create and activate a Python virtual environment:
   - **Windows PowerShell:**
     ```powershell
     python -m venv .venv
     .\.venv\Scripts\Activate.ps1
     ```
     *(If script execution is disabled on Windows, run: `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`)*
   - **macOS / Linux:**
     ```bash
     python3 -m venv .venv
     source .venv/bin/activate
     ```

3. Install required Python packages:
   ```bash
   pip install --upgrade pip
   pip install -r requirements.txt
   ```

4. Launch the AI Worker:
   ```bash
   python -m app.main
   ```

5. **What happens on startup:**
   - Probes for NVIDIA CUDA GPU devices. If no GPU is found, it automatically logs:
     `"No GPU detected, falling back to CPU device with int8 compute type"`
   - Connects to RabbitMQ `transcription.jobs` queue.
   - Starts a lightweight FastAPI health server on port `8081`.

6. **Verify Worker Health:**
   ```bash
   curl http://localhost:8081/health
   # Returns: {"status":"healthy","worker":"videototext-ai-worker","version":"1.0.0"}
   ```

---

### Step 5: Run Angular Frontend (Node 20)

1. Open a new terminal and navigate to `frontend/`:
   ```bash
   cd frontend
   ```

2. Install Node dependencies:
   ```bash
   npm install
   ```

3. Start the Angular development server:
   ```bash
   npm start
   ```
   *(Or: `npx ng serve`)*

4. Open your browser and navigate to:
   ```
   http://localhost:4200
   ```

---

## 4. First-Time Walkthrough (Testing the Platform)

Once all 3 applications are running:

1. **User Registration:**
   - Open `http://localhost:4200/register`.
   - Register an account with your email and a secure password (e.g., `Admin123!@#`).
   - Log in with your new credentials.

2. **Video Upload:**
   - Navigate to the **Upload Video** tab.
   - Drag and drop a sample `.mp4` video (e.g. 10 to 60 seconds for quick testing).
   - The UI uploads the file to MinIO, validates binary magic bytes, and extracts metadata via FFprobe.

3. **Start Transcription:**
   - In your **Video Library**, click **Transcribe** on your uploaded video.
   - Options to configure:
     - **Model Size:** `small` or `base` (recommended for fast CPU execution).
     - **Speaker Diarization:** Toggle ON to test speaker identification.
     - **Translation:** Select `Hindi [HI]` or `Marathi [MR]`.
     - **AI Summary:** Toggle ON to test executive insights.
   - Click **Start Transcription**.

4. **Observe Real-Time Updates:**
   - The UI opens the live monitoring view backed by Server-Sent Events (SSE).
   - Watch the visual stepper progress:
     `Audio Extraction -> VAD Chunking -> ASR Inference -> Diarization -> Translation -> Summary -> Finalization`.

5. **Interact with the Transcript:**
   - **Click-to-Seek:** Click any sentence in the transcript to seek the HTML5 video preview to that exact timestamp.
   - **Audio Waveform:** Scrub the canvas waveform visualizer and adjust playback speeds (`1.0x` – `2.0x`).
   - **In-Place Editing:** Click the edit icon on any transcript segment to modify words or timestamps.
   - **Multilingual Tabs:** Switch between **Original (EN)**, **हिन्दी (HI)**, and **मराठी (MR)** tabs.
   - **Subtitle Download:** Click **Export** to download `.srt` or `.vtt` subtitle files.

---

## 5. Running Automated Test Suites

You can verify all platform layers locally with zero dependencies:

### 5.1 Backend Tests (121 tests)
Runs integration tests with an in-memory H2 database, MockMvc, and Mock S3/RabbitMQ:
```bash
cd backend
mvn test
```

### 5.2 AI Worker Tests (96 tests)
Runs unit, integration, and performance benchmark suites:
```bash
cd ai-worker
# Activate virtual environment first
pytest
```

### 5.3 Frontend Tests (32 tests)
Runs Jasmine/Karma tests in ChromeHeadless:
```bash
cd frontend
npm test -- --watch=false --browsers=ChromeHeadless
```

**Total Platform Suite:** **249 / 249 tests passing (100%)**.

---

## 6. Troubleshooting & FAQs

### Q: Port already in use (5432, 5672, 6379, 8080, 4200)?
- **PostgreSQL (5432):** If you already have a local PostgreSQL instance running, either stop it (`services.msc` on Windows or `sudo systemctl stop postgresql`) or change `POSTGRES_PORT` in `.env` and `docker-compose.yml`.
- **Backend (8080):** If port 8080 is occupied, set `SERVER_PORT=8082` in `backend/src/main/resources/application.yml` or launch with `mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8082"`.

### Q: Windows PowerShell script execution error when activating venv?
Run:
```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\.venv\Scripts\Activate.ps1
```

### Q: FFmpeg not detected by AI Worker or Backend?
Ensure `ffmpeg` and `ffprobe` are in your system `PATH`:
```bash
ffmpeg -version
```
If using a custom binary path, set `FFMPEG_PATH=/custom/path/ffmpeg` in `.env`.

### Q: How do I reset the local database and S3 storage?
To completely wipe and restart the local environment:
```bash
docker compose down -v
docker compose up -d
```
The Flyway migrations will automatically recreate the database schema from scratch on backend startup.

### Q: Can I run without an NVIDIA GPU?
**Yes!** The AI Worker automatically detects hardware availability. If CUDA is not detected, it switches to CPU mode using `int8` quantization. For fast local CPU execution, choose the `tiny`, `base`, or `small` Whisper models.

---

## 7. Next Steps & Further Reading

- [User Guide](docs/user-guide.md) — Comprehensive guide to the platform's features.
- [Deployment Runbook](docs/deployment-runbook.md) — Production Docker Compose, Kubernetes, and TLS setup.
- [API Specification](docs/api-specification.md) — Full REST API contracts and schema models.
- [Performance Benchmarks](docs/performance-strategy.md) — 4-hour video SLA benchmarks and GPU metrics.
