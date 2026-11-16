import json
import logging
from typing import Optional
import pika
from app.config.settings import WorkerSettings, get_settings
from app.messaging.rabbitmq_client import RabbitMQClient
from app.models.event import ProcessingEvent
from app.models.job import TranscriptionJobResult

logger = logging.getLogger(__name__)


class EventPublisher:
    """Publishes progress events and completion results to RabbitMQ exchange."""

    def __init__(
        self,
        client: RabbitMQClient,
        settings: Optional[WorkerSettings] = None
    ):
        self.client = client
        self.settings = settings or get_settings()

    def publish_event(self, event: ProcessingEvent) -> bool:
        """Publishes a live progress/stage update event to processing.events queue."""
        try:
            channel = self.client.get_channel()
            payload = event.model_dump_json(by_alias=True)
            properties = pika.BasicProperties(
                content_type="application/json",
                delivery_mode=pika.DeliveryMode.Persistent,
                headers={"jobId": str(event.job_id), "stage": event.stage.value}
            )

            channel.basic_publish(
                exchange=self.settings.RABBITMQ_MAIN_EXCHANGE,
                routing_key=self.settings.RABBITMQ_EVENTS_ROUTING_KEY,
                body=payload.encode("utf-8"),
                properties=properties
            )
            logger.debug(
                "Published event [job_id:%s, stage:%s, progress:%.1f%%]",
                event.job_id,
                event.stage.value,
                event.progress_percent
            )
            return True
        except Exception as err:
            logger.error("Failed to publish progress event for job %s: %s", event.job_id, err)
            return False

    def publish_result(self, result: TranscriptionJobResult) -> bool:
        """Publishes job completion/failure result to transcription.results queue."""
        try:
            channel = self.client.get_channel()
            payload = result.model_dump_json(by_alias=True)
            properties = pika.BasicProperties(
                content_type="application/json",
                delivery_mode=pika.DeliveryMode.Persistent,
                headers={"jobId": str(result.job_id), "status": result.status}
            )

            channel.basic_publish(
                exchange=self.settings.RABBITMQ_RESULTS_EXCHANGE,
                routing_key=self.settings.RABBITMQ_RESULTS_ROUTING_KEY,
                body=payload.encode("utf-8"),
                properties=properties
            )
            logger.info(
                "Published result [job_id:%s, status:%s, duration:%.1fs, segments:%d]",
                result.job_id,
                result.status,
                result.duration_seconds,
                result.segments_count
            )
            return True
        except Exception as err:
            logger.error("Failed to publish job result for job %s: %s", result.job_id, err)
            return False
