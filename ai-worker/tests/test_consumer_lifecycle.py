import json
import uuid
from unittest.mock import MagicMock
from app.consumers.transcription_consumer import TranscriptionConsumer
from app.consumers.idempotency import IdempotencyManager
from app.consumers.retry_handler import RetryHandler
from app.messaging.publisher import EventPublisher
from app.messaging.rabbitmq_client import RabbitMQClient
from app.processors.base_processor import DefaultJobProcessor


def create_mock_delivery(job_id=None, video_id=None, user_id=None):
    job_id = job_id or uuid.uuid4()
    video_id = video_id or uuid.uuid4()
    user_id = user_id or uuid.uuid4()

    payload = {
        "jobId": str(job_id),
        "videoId": str(video_id),
        "userId": str(user_id),
        "s3Key": "videos/sample.mp4",
        "language": "en",
        "engine": "faster-whisper",
        "model": "small",
        "enableDiarization": False
    }

    body = json.dumps(payload).encode("utf-8")
    method = MagicMock()
    method.delivery_tag = 42
    properties = MagicMock()
    return job_id, method, properties, body


def test_consumer_acknowledges_successful_job():
    mock_client = MagicMock(spec=RabbitMQClient)
    mock_publisher = MagicMock(spec=EventPublisher)
    idempotency = IdempotencyManager(redis_client=None)
    retry_handler = RetryHandler(redis_client=None)
    processor = DefaultJobProcessor(publisher=mock_publisher)

    consumer = TranscriptionConsumer(
        client=mock_client,
        publisher=mock_publisher,
        processor=processor,
        idempotency=idempotency,
        retry_handler=retry_handler
    )

    mock_channel = MagicMock()
    job_id, method, properties, body = create_mock_delivery()

    consumer.on_message(mock_channel, method, properties, body)

    # Verify basic_ack was called with the delivery tag
    mock_channel.basic_ack.assert_called_once_with(delivery_tag=42)
    mock_channel.basic_nack.assert_not_called()

    # Verify marked as completed in idempotency
    assert idempotency.is_already_completed(str(job_id)) is True


def test_consumer_skips_and_acks_already_completed_job():
    mock_client = MagicMock(spec=RabbitMQClient)
    mock_publisher = MagicMock(spec=EventPublisher)
    idempotency = IdempotencyManager(redis_client=None)
    retry_handler = RetryHandler(redis_client=None)
    mock_processor = MagicMock()

    consumer = TranscriptionConsumer(
        client=mock_client,
        publisher=mock_publisher,
        processor=mock_processor,
        idempotency=idempotency,
        retry_handler=retry_handler
    )

    mock_channel = MagicMock()
    job_id, method, properties, body = create_mock_delivery()

    # Pre-mark job as completed
    idempotency.mark_completed(str(job_id))

    consumer.on_message(mock_channel, method, properties, body)

    # Should ack immediately without calling processor
    mock_channel.basic_ack.assert_called_once_with(delivery_tag=42)
    mock_processor.process_job.assert_not_called()


def test_consumer_rejects_malformed_json_to_dlq():
    mock_client = MagicMock(spec=RabbitMQClient)
    mock_publisher = MagicMock(spec=EventPublisher)
    idempotency = IdempotencyManager(redis_client=None)
    retry_handler = RetryHandler(redis_client=None)

    consumer = TranscriptionConsumer(
        client=mock_client,
        publisher=mock_publisher,
        idempotency=idempotency,
        retry_handler=retry_handler
    )

    mock_channel = MagicMock()
    method = MagicMock()
    method.delivery_tag = 99
    properties = MagicMock()
    bad_body = b"this is completely invalid json {"

    consumer.on_message(mock_channel, method, properties, bad_body)

    # Malformed messages must be rejected without requeue (sent to DLQ)
    mock_channel.basic_nack.assert_called_once_with(delivery_tag=99, requeue=False)
    mock_channel.basic_ack.assert_not_called()


def test_consumer_retries_transient_failure():
    mock_client = MagicMock(spec=RabbitMQClient)
    mock_publisher = MagicMock(spec=EventPublisher)
    idempotency = IdempotencyManager(redis_client=None)
    retry_handler = RetryHandler(redis_client=None)
    mock_processor = MagicMock()
    mock_processor.process_job.side_effect = RuntimeError("Temporary S3 download failure")

    consumer = TranscriptionConsumer(
        client=mock_client,
        publisher=mock_publisher,
        processor=mock_processor,
        idempotency=idempotency,
        retry_handler=retry_handler
    )

    mock_channel = MagicMock()
    job_id, method, properties, body = create_mock_delivery()

    consumer.on_message(mock_channel, method, properties, body)

    # On first failure: requeue=True
    mock_channel.basic_nack.assert_called_once_with(delivery_tag=42, requeue=True)
    assert retry_handler.get_attempts(str(job_id)) == 1


def test_consumer_escalates_to_dlq_when_max_retries_exceeded():
    mock_client = MagicMock(spec=RabbitMQClient)
    mock_publisher = MagicMock(spec=EventPublisher)
    idempotency = IdempotencyManager(redis_client=None)
    retry_handler = RetryHandler(redis_client=None)
    mock_processor = MagicMock()
    mock_processor.process_job.side_effect = RuntimeError("Permanent failure")

    consumer = TranscriptionConsumer(
        client=mock_client,
        publisher=mock_publisher,
        processor=mock_processor,
        idempotency=idempotency,
        retry_handler=retry_handler
    )

    mock_channel = MagicMock()
    job_id, method, properties, body = create_mock_delivery()

    # Simulate 3 previous failed attempts
    for _ in range(3):
        retry_handler.record_failure(str(job_id), "prior error")

    assert retry_handler.get_attempts(str(job_id)) == 3

    consumer.on_message(mock_channel, method, properties, body)

    # 4th failure exceeds MAX_RETRIES (3) -> requeue=False (sent to DLQ)
    mock_channel.basic_nack.assert_called_once_with(delivery_tag=42, requeue=False)
    mock_publisher.publish_result.assert_called_once()
