import logging
import time
from typing import Optional
import pika
from pika.adapters.blocking_connection import BlockingChannel
from pika.exceptions import AMQPConnectionError, AMQPChannelError
from app.config.settings import WorkerSettings, get_settings

logger = logging.getLogger(__name__)


class RabbitMQClient:
    """Manages persistent connection, channels, and topology setup for RabbitMQ."""

    def __init__(self, settings: Optional[WorkerSettings] = None):
        self.settings = settings or get_settings()
        self._connection: Optional[pika.BlockingConnection] = None
        self._channel: Optional[BlockingChannel] = None

    def connect(self, max_retries: int = 5, retry_delay: float = 2.0) -> pika.BlockingConnection:
        """Establishes connection to RabbitMQ with retry on startup."""
        credentials = pika.PlainCredentials(
            self.settings.RABBITMQ_USER, self.settings.RABBITMQ_PASSWORD
        )
        parameters = pika.ConnectionParameters(
            host=self.settings.RABBITMQ_HOST,
            port=self.settings.RABBITMQ_PORT,
            virtual_host=self.settings.RABBITMQ_VHOST,
            credentials=credentials,
            heartbeat=self.settings.RABBITMQ_HEARTBEAT_SECONDS,
            blocked_connection_timeout=300
        )

        for attempt in range(1, max_retries + 1):
            try:
                logger.info(
                    "Connecting to RabbitMQ at %s:%d/%s (attempt %d/%d)...",
                    self.settings.RABBITMQ_HOST,
                    self.settings.RABBITMQ_PORT,
                    self.settings.RABBITMQ_VHOST,
                    attempt,
                    max_retries
                )
                self._connection = pika.BlockingConnection(parameters)
                self._channel = self._connection.channel()
                self._setup_topology()
                logger.info("Successfully connected to RabbitMQ and initialized topology.")
                return self._connection
            except AMQPConnectionError as err:
                logger.warning(
                    "Failed to connect to RabbitMQ (attempt %d/%d): %s",
                    attempt,
                    max_retries,
                    err
                )
                if attempt == max_retries:
                    raise
                time.sleep(retry_delay * attempt)

        raise AMQPConnectionError("Unable to establish connection to RabbitMQ after retries.")

    def _setup_topology(self) -> None:
        """Declares exchanges, queues, and bindings matching backend contracts."""
        if not self._channel:
            raise RuntimeError("Cannot setup topology without an active channel.")

        # Exchanges
        self._channel.exchange_declare(
            exchange=self.settings.RABBITMQ_MAIN_EXCHANGE,
            exchange_type="topic",
            durable=True
        )
        self._channel.exchange_declare(
            exchange=self.settings.RABBITMQ_DLX_EXCHANGE,
            exchange_type="direct",
            durable=True
        )

        # Dead Letter Queue
        self._channel.queue_declare(
            queue="transcription.dlq",
            durable=True
        )
        self._channel.queue_bind(
            exchange=self.settings.RABBITMQ_DLX_EXCHANGE,
            queue="transcription.dlq",
            routing_key=self.settings.RABBITMQ_DLQ_ROUTING_KEY
        )

        # Requests queue with Dead Letter routing
        self._channel.queue_declare(
            queue=self.settings.RABBITMQ_QUEUE,
            durable=True,
            arguments={
                "x-dead-letter-exchange": self.settings.RABBITMQ_DLX_EXCHANGE,
                "x-dead-letter-routing-key": self.settings.RABBITMQ_DLQ_ROUTING_KEY
            }
        )
        self._channel.queue_bind(
            exchange=self.settings.RABBITMQ_MAIN_EXCHANGE,
            queue=self.settings.RABBITMQ_QUEUE,
            routing_key="transcription.request"
        )

        # Results & Events queues
        self._channel.queue_declare(queue="transcription.results", durable=True)
        self._channel.queue_bind(
            exchange=self.settings.RABBITMQ_MAIN_EXCHANGE,
            queue="transcription.results",
            routing_key=self.settings.RABBITMQ_RESULTS_ROUTING_KEY
        )

        self._channel.queue_declare(queue="processing.events", durable=True)
        self._channel.queue_bind(
            exchange=self.settings.RABBITMQ_MAIN_EXCHANGE,
            queue="processing.events",
            routing_key=self.settings.RABBITMQ_EVENTS_ROUTING_KEY
        )

        # Fair dispatch: worker only gets 1 message at a time
        self._channel.basic_qos(prefetch_count=self.settings.RABBITMQ_PREFETCH_COUNT)

    def get_channel(self) -> BlockingChannel:
        """Returns active channel, reconnecting if closed."""
        if self._connection is None or self._connection.is_closed:
            self.connect()
        elif self._channel is None or self._channel.is_closed:
            self._channel = self._connection.channel()
            self._setup_topology()
        return self._channel

    def is_connected(self) -> bool:
        """Checks if current RabbitMQ connection is active."""
        return (
            self._connection is not None
            and not self._connection.is_closed
            and self._channel is not None
            and not self._channel.is_closed
        )

    def close(self) -> None:
        """Gracefully closes channel and connection."""
        try:
            if self._channel and not self._channel.is_closed:
                self._channel.close()
        except Exception as e:
            logger.debug("Error closing channel: %s", e)

        try:
            if self._connection and not self._connection.is_closed:
                self._connection.close()
                logger.info("RabbitMQ connection closed gracefully.")
        except Exception as e:
            logger.debug("Error closing connection: %s", e)
