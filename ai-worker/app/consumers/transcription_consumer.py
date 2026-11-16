import json
import logging
import time
from typing import Optional
import pika
from pika.adapters.blocking_connection import BlockingChannel
from pika.spec import Basic, BasicProperties
from app.config.settings import WorkerSettings, get_settings
from app.consumers.idempotency import IdempotencyManager
from app.consumers.retry_handler import RetryHandler
from app.messaging.publisher import EventPublisher
from app.messaging.rabbitmq_client import RabbitMQClient
from app.models.event import ProcessingStage
from app.models.job import TranscriptionJobRequest, TranscriptionJobResult
from app.processors.base_processor import BaseJobProcessor, DefaultJobProcessor

logger = logging.getLogger(__name__)


class TranscriptionConsumer:
    """RabbitMQ consumer that processes incoming transcription jobs with idempotency and retry handling."""

    def __init__(
        self,
        client: Optional[RabbitMQClient] = None,
        publisher: Optional[EventPublisher] = None,
        processor: Optional[BaseJobProcessor] = None,
        idempotency: Optional[IdempotencyManager] = None,
        retry_handler: Optional[RetryHandler] = None,
        settings: Optional[WorkerSettings] = None
    ):
        self.settings = settings or get_settings()
        self.client = client or RabbitMQClient(self.settings)
        self.publisher = publisher or EventPublisher(self.client, self.settings)
        self.processor = processor or DefaultJobProcessor(self.publisher)
        self.idempotency = idempotency or IdempotencyManager(self.settings)
        self.retry_handler = retry_handler or RetryHandler(self.settings)
        self._is_running = False
        self._consumer_tag: Optional[str] = None

    def start_consuming(self) -> None:
        """Connects to RabbitMQ and starts blocking consumption loop."""
        channel = self.client.get_channel()
        channel.basic_qos(prefetch_count=self.settings.RABBITMQ_PREFETCH_COUNT)
        self._is_running = True

        logger.info(
            "Worker [%s] starting consumer loop on queue '%s' (prefetch=%d)...",
            self.settings.WORKER_NAME,
            self.settings.RABBITMQ_QUEUE,
            self.settings.RABBITMQ_PREFETCH_COUNT
        )

        self._consumer_tag = channel.basic_consume(
            queue=self.settings.RABBITMQ_QUEUE,
            on_message_callback=self.on_message,
            auto_ack=False
        )

        try:
            channel.start_consuming()
        except KeyboardInterrupt:
            logger.info("KeyboardInterrupt received. Stopping consumer...")
            self.stop_consuming()
        except Exception as e:
            logger.error("Consumer loop encountered fatal error: %s", e)
            self.stop_consuming()
            raise

    def stop_consuming(self) -> None:
        """Gracefully halts message consumption."""
        self._is_running = False
        try:
            channel = self.client.get_channel()
            if self._consumer_tag and channel.is_open:
                channel.basic_cancel(self._consumer_tag)
            if channel.is_open:
                channel.stop_consuming()
        except Exception as e:
            logger.debug("Error while stopping consumer: %s", e)
        finally:
            self.client.close()
            logger.info("Consumer stopped cleanly.")

    def on_message(
        self,
        channel: BlockingChannel,
        method: Basic.Deliver,
        properties: BasicProperties,
        body: bytes
    ) -> None:
        """Callback executed when a new job message arrives from the queue."""
        delivery_tag = method.delivery_tag
        raw_payload = body.decode("utf-8")

        try:
            data = json.loads(raw_payload)
            job = TranscriptionJobRequest.model_validate(data)
        except Exception as parse_err:
            logger.error("Failed to parse incoming message into TranscriptionJobRequest: %s. Rejecting to DLQ.", parse_err)
            # Malformed payload cannot be processed or retried - discard to DLQ
            channel.basic_nack(delivery_tag=delivery_tag, requeue=False)
            return

        job_id_str = str(job.job_id)
        logger.info("Received job request: %s (video: %s, engine: %s)", job_id_str, job.video_id, job.engine)

        # 1. Idempotency Check: Skip duplicate execution if already completed
        if self.idempotency.is_already_completed(job_id_str):
            logger.info("Job %s has already been completed. Acknowledging duplicate delivery.", job_id_str)
            channel.basic_ack(delivery_tag=delivery_tag)
            return

        # 2. Concurrency Lock: Prevent simultaneous executions of the same job
        if not self.idempotency.acquire_lock(job_id_str):
            logger.warning("Job %s is currently locked by another worker or in-progress. Acknowledging message.", job_id_str)
            channel.basic_ack(delivery_tag=delivery_tag)
            return

        # 3. Processing Execution
        try:
            result = self.processor.process_job(job)
            self.idempotency.mark_completed(job_id_str)
            self.retry_handler.reset_retries(job_id_str)
            channel.basic_ack(delivery_tag=delivery_tag)
            logger.info("Job %s processed and acknowledged successfully.", job_id_str)
        except Exception as exc:
            error_msg = str(exc)
            logger.error("Error processing job %s: %s", job_id_str, error_msg, exc_info=True)
            self.idempotency.mark_failed(job_id_str)

            should_retry, delay, attempt = self.retry_handler.record_failure(job_id_str, error_msg)

            if should_retry:
                # Transient error: sleep backoff and requeue
                if delay > 0:
                    time.sleep(delay)
                channel.basic_nack(delivery_tag=delivery_tag, requeue=True)
            else:
                # Exceeded max retries: send failure event and escalate to DLQ
                self.processor.emit_progress(
                    job.job_id,
                    ProcessingStage.FAILED,
                    100.0,
                    f"Processing failed permanently after {attempt} attempts: {error_msg}"
                )
                failure_result = TranscriptionJobResult(
                    job_id=job.job_id,
                    video_id=job.video_id,
                    status="FAILED",
                    duration_seconds=0.0,
                    error_message=error_msg
                )
                self.publisher.publish_result(failure_result)
                channel.basic_nack(delivery_tag=delivery_tag, requeue=False)
