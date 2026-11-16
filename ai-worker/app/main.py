import argparse
import logging
import sys
from fastapi import FastAPI, Response, status
import uvicorn
from app.config.logging import setup_logging
from app.config.settings import get_settings
from app.consumers.transcription_consumer import TranscriptionConsumer
from app.health.checker import HealthChecker
from app.models.health import ServiceStatus, WorkerHealthResponse

# Initialize logging
settings = get_settings()
setup_logging(level=settings.LOG_LEVEL)
logger = logging.getLogger("ai-worker")

# FastAPI instance for container orchestration & health monitoring
api_app = FastAPI(
    title=settings.WORKER_NAME,
    version=settings.WORKER_VERSION,
    description="VideoToText AI Worker Health, Monitoring, and Readiness API"
)

health_checker = HealthChecker(settings)


@api_app.get("/health", tags=["Health"])
def health_liveness():
    """Kubernetes / Docker liveness probe."""
    return {"status": "UP", "worker": settings.WORKER_NAME, "version": settings.WORKER_VERSION}


@api_app.get("/health/detailed", response_model=WorkerHealthResponse, tags=["Health"])
def health_readiness(response: Response):
    """Kubernetes / Docker readiness check inspecting RabbitMQ, Redis, and MinIO."""
    report = health_checker.get_full_health(active_jobs=0)
    if report.status == ServiceStatus.DOWN:
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
    elif report.status == ServiceStatus.DEGRADED:
        response.status_code = status.HTTP_200_OK
    return report


@api_app.get("/info", tags=["Info"])
def worker_info():
    """Runtime configuration summary."""
    return {
        "worker_name": settings.WORKER_NAME,
        "version": settings.WORKER_VERSION,
        "environment": settings.ENVIRONMENT,
        "default_engine": settings.ASR_DEFAULT_ENGINE,
        "default_model": settings.ASR_DEFAULT_MODEL,
        "compute_type": settings.ASR_COMPUTE_TYPE,
        "device": settings.ASR_DEVICE,
        "max_duration_minutes": settings.MAX_VIDEO_DURATION_MINUTES,
        "rabbitmq_queue": settings.RABBITMQ_QUEUE
    }


def start_consumer():
    """Starts the blocking RabbitMQ worker consumer."""
    logger.info("==================================================")
    logger.info("Starting VideoToText AI Worker Consumer")
    logger.info("Worker Name: %s (v%s)", settings.WORKER_NAME, settings.WORKER_VERSION)
    logger.info("ASR Engine: %s (%s, %s)", settings.ASR_DEFAULT_ENGINE, settings.ASR_DEFAULT_MODEL, settings.ASR_DEVICE)
    logger.info("RabbitMQ Queue: %s (host: %s:%d)", settings.RABBITMQ_QUEUE, settings.RABBITMQ_HOST, settings.RABBITMQ_PORT)
    logger.info("==================================================")

    consumer = TranscriptionConsumer(settings=settings)
    consumer.start_consuming()


def main():
    parser = argparse.ArgumentParser(description="VideoToText AI Worker")
    parser.add_argument(
        "--api",
        action="store_true",
        help="Run the FastAPI health check & metrics server instead of consumer loop"
    )
    args = parser.parse_args()

    if args.api:
        logger.info("Starting AI Worker Health API on %s:%d", settings.HEALTH_HOST, settings.HEALTH_PORT)
        uvicorn.run(api_app, host=settings.HEALTH_HOST, port=settings.HEALTH_PORT)
    else:
        start_consumer()


if __name__ == "__main__":
    main()
