import time
import logging
from typing import Optional
import redis
import pika
import boto3
from botocore.client import Config
from app.config.settings import WorkerSettings, get_settings
from app.models.health import ComponentHealth, ServiceStatus, WorkerHealthResponse

logger = logging.getLogger(__name__)


class HealthChecker:
    """Verifies operational health of worker dependencies (RabbitMQ, Redis, Object Storage)."""

    def __init__(self, settings: Optional[WorkerSettings] = None):
        self.settings = settings or get_settings()

    def check_rabbitmq(self) -> ComponentHealth:
        start = time.time()
        try:
            credentials = pika.PlainCredentials(
                self.settings.RABBITMQ_USER, self.settings.RABBITMQ_PASSWORD
            )
            parameters = pika.ConnectionParameters(
                host=self.settings.RABBITMQ_HOST,
                port=self.settings.RABBITMQ_PORT,
                virtual_host=self.settings.RABBITMQ_VHOST,
                credentials=credentials,
                connection_attempts=1,
                socket_timeout=1.5
            )
            conn = pika.BlockingConnection(parameters)
            conn.close()
            latency = (time.time() - start) * 1000
            return ComponentHealth(status=ServiceStatus.UP, latency_ms=round(latency, 2))
        except Exception as e:
            latency = (time.time() - start) * 1000
            return ComponentHealth(
                status=ServiceStatus.DOWN,
                latency_ms=round(latency, 2),
                details={"error": str(e)}
            )

    def check_redis(self) -> ComponentHealth:
        start = time.time()
        try:
            r = redis.Redis(
                host=self.settings.REDIS_HOST,
                port=self.settings.REDIS_PORT,
                password=self.settings.REDIS_PASSWORD or None,
                db=self.settings.REDIS_DB,
                socket_timeout=1.5
            )
            r.ping()
            latency = (time.time() - start) * 1000
            return ComponentHealth(status=ServiceStatus.UP, latency_ms=round(latency, 2))
        except Exception as e:
            latency = (time.time() - start) * 1000
            return ComponentHealth(
                status=ServiceStatus.DOWN,
                latency_ms=round(latency, 2),
                details={"error": str(e)}
            )

    def check_s3(self) -> ComponentHealth:
        start = time.time()
        try:
            s3_client = boto3.client(
                "s3",
                endpoint_url=self.settings.MINIO_ENDPOINT,
                aws_access_key_id=self.settings.MINIO_ACCESS_KEY,
                aws_secret_access_key=self.settings.MINIO_SECRET_KEY,
                region_name=self.settings.S3_REGION,
                config=Config(connect_timeout=1.5, retries={"max_attempts": 1})
            )
            s3_client.list_buckets()
            latency = (time.time() - start) * 1000
            return ComponentHealth(status=ServiceStatus.UP, latency_ms=round(latency, 2))
        except Exception as e:
            latency = (time.time() - start) * 1000
            return ComponentHealth(
                status=ServiceStatus.DEGRADED,
                latency_ms=round(latency, 2),
                details={"error": str(e)}
            )

    def get_full_health(self, active_jobs: int = 0) -> WorkerHealthResponse:
        """Aggregates component checks into comprehensive worker health report."""
        components = {
            "rabbitmq": self.check_rabbitmq(),
            "redis": self.check_redis(),
            "storage": self.check_s3()
        }

        # If RabbitMQ is down, the worker cannot consume jobs -> DOWN
        if components["rabbitmq"].status == ServiceStatus.DOWN:
            overall = ServiceStatus.DOWN
        elif any(c.status in (ServiceStatus.DOWN, ServiceStatus.DEGRADED) for c in components.values()):
            overall = ServiceStatus.DEGRADED
        else:
            overall = ServiceStatus.UP

        return WorkerHealthResponse(
            status=overall,
            worker_name=self.settings.WORKER_NAME,
            worker_version=self.settings.WORKER_VERSION,
            active_jobs=active_jobs,
            components=components
        )
