from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


class WorkerSettings(BaseSettings):
    """Central configuration for AI Worker using Pydantic Settings."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore"
    )

    # Worker Info
    WORKER_NAME: str = "videototext-ai-worker"
    WORKER_VERSION: str = "0.1.0"
    ENVIRONMENT: str = "development"
    LOG_LEVEL: str = "INFO"

    # RabbitMQ
    RABBITMQ_HOST: str = "localhost"
    RABBITMQ_PORT: int = 5672
    RABBITMQ_USER: str = "videototext"
    RABBITMQ_PASSWORD: str = "videototext_password"
    RABBITMQ_VHOST: str = "videototext"
    RABBITMQ_QUEUE: str = "transcription.requests"
    RABBITMQ_MAIN_EXCHANGE: str = "videototext.exchange"
    RABBITMQ_RESULTS_EXCHANGE: str = "videototext.exchange"
    RABBITMQ_RESULTS_ROUTING_KEY: str = "transcription.result"
    RABBITMQ_EVENTS_ROUTING_KEY: str = "processing.event"
    RABBITMQ_DLX_EXCHANGE: str = "videototext.dlx"
    RABBITMQ_DLQ_ROUTING_KEY: str = "transcription.dead"
    RABBITMQ_PREFETCH_COUNT: int = 1
    RABBITMQ_HEARTBEAT_SECONDS: int = 60

    # Redis
    REDIS_HOST: str = "localhost"
    REDIS_PORT: int = 6379
    REDIS_PASSWORD: str = "videototext_password"
    REDIS_DB: int = 0
    IDEMPOTENCY_TTL_SECONDS: int = 86400  # 24 hours
    IDEMPOTENCY_KEY_PREFIX: str = "job:idempotency:"
    RETRY_KEY_PREFIX: str = "job:retries:"

    # PostgreSQL
    POSTGRES_HOST: str = "localhost"
    POSTGRES_PORT: int = 5432
    POSTGRES_DB: str = "videototext"
    POSTGRES_USER: str = "videototext"
    POSTGRES_PASSWORD: str = "videototext_password"

    # Storage (S3 / MinIO)
    MINIO_ENDPOINT: str = "http://localhost:9000"
    MINIO_ACCESS_KEY: str = "minioadmin"
    MINIO_SECRET_KEY: str = "minioadmin"
    MINIO_BUCKET: str = "videototext-media"
    S3_REGION: str = "us-east-1"
    S3_USE_SSL: bool = False

    # Processing Defaults
    ASR_DEFAULT_ENGINE: str = "faster-whisper"
    ASR_DEFAULT_MODEL: str = "small"
    ASR_COMPUTE_TYPE: str = "int8_float16"
    ASR_DEVICE: str = "auto"
    CHUNK_DURATION_SECONDS: int = 600
    MAX_VIDEO_DURATION_MINUTES: int = 240

    # Retry Strategy
    MAX_RETRIES: int = 3
    INITIAL_RETRY_DELAY_SECONDS: float = 2.0
    BACKOFF_MULTIPLIER: float = 2.0

    # Health Check API
    HEALTH_HOST: str = "0.0.0.0"
    HEALTH_PORT: int = 8000


@lru_cache
def get_settings() -> WorkerSettings:
    return WorkerSettings()
