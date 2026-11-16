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
    RABBITMQ_RESULTS_EXCHANGE: str = "videototext.exchange"
    RABBITMQ_RESULTS_ROUTING_KEY: str = "transcription.result"
    RABBITMQ_EVENTS_ROUTING_KEY: str = "processing.event"

    # Redis
    REDIS_HOST: str = "localhost"
    REDIS_PORT: int = 6379
    REDIS_PASSWORD: str = "videototext_password"

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
    MINIO_BUCKET: str = "videototext"

    # Processing Defaults
    ASR_DEFAULT_ENGINE: str = "faster-whisper"
    ASR_DEFAULT_MODEL: str = "small"
    ASR_COMPUTE_TYPE: str = "int8_float16"
    ASR_DEVICE: str = "auto"
    CHUNK_DURATION_SECONDS: int = 600
    MAX_VIDEO_DURATION_MINUTES: int = 240


@lru_cache
def get_settings() -> WorkerSettings:
    return WorkerSettings()
