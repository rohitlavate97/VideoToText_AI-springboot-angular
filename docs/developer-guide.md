# Developer Guide — VideoToText AI

## 1. Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 21 (LTS) | OpenJDK or Oracle JDK |
| Maven | 3.9+ | Build tool for Spring Boot |
| Node.js | 20 LTS | For Angular CLI |
| npm | 10+ | Comes with Node.js |
| Angular CLI | 17.x | `npm install -g @angular/cli@17` |
| Python | 3.11 | For AI worker |
| Docker | 24+ | Container runtime |
| Docker Compose | 2.20+ | Multi-container orchestration |
| FFmpeg | 6.x+ | Audio/video processing |
| Git | 2.40+ | Version control |
| NVIDIA Driver | 555+ | For GPU acceleration (optional for dev) |
| CUDA Toolkit | 12.x | For GPU-accelerated inference |

## 2. Repository Structure

```
VideoToText_AI-springboot-angular/
├── backend/          # Spring Boot 3.5.x (Java 21)
├── frontend/         # Angular 17
├── ai-worker/        # Python 3.11 AI worker
├── infrastructure/   # Docker, Nginx, scripts
├── docs/             # All documentation
├── docker-compose.yml
├── .gitignore
└── README.md
```

## 3. Quick Start

```bash
# 1. Clone
git clone <repo-url>
cd VideoToText_AI-springboot-angular

# 2. Start infrastructure
docker compose up -d postgres rabbitmq redis minio

# 3. Start backend
cd backend
mvn spring-boot:run

# 4. Start frontend
cd frontend
npm install
ng serve

# 5. Start AI worker
cd ai-worker
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
python -m app.main
```

## 4. Git Workflow

- `main` — stable release branch
- `develop` — integration branch
- `feature/<name>` — feature branches
- `bugfix/<name>` — bug fix branches

Commit messages follow Conventional Commits (e.g., `feat(video): implement upload`).

## 5. Environment Variables

See `.env.example` for all required variables. Never commit `.env` files.
