# =============================================================================
# VideoToText AI — Production AI Worker (CPU / Base)
# =============================================================================
FROM python:3.11-slim-bookworm AS runtime

LABEL maintainer="VideoToText AI Team"
LABEL description="VideoToText AI Worker (CPU Optimized)"

WORKDIR /app

# Install system dependencies: FFmpeg, FFprobe, libsndfile, curl
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    curl \
    gcc \
    libpq-dev \
    libsndfile1 \
    && rm -rf /var/lib/apt/lists/*

# Security: Run as non-root user
RUN groupadd -g 10001 workergroup && \
    useradd -u 10001 -g workergroup -m -s /bin/bash workeruser

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY app/ ./app/

# Create work directories with proper ownership
RUN mkdir -p /app/temp /app/models /app/logs && \
    chown -R workeruser:workergroup /app

USER workeruser

# Production environment variables
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONPATH=/app \
    WORKER_CONCURRENCY=1 \
    DEFAULT_COMPUTE_TYPE=int8 \
    MODEL_CACHE_DIR=/app/models

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:8081/health || exit 1

CMD ["python", "-m", "app.main"]
