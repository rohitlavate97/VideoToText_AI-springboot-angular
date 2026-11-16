import logging
import time
from typing import Optional, Tuple
import redis
from app.config.settings import WorkerSettings, get_settings

logger = logging.getLogger(__name__)


class RetryHandler:
    """Manages job retry counters, exponential backoff, and dead-letter escalation."""

    def __init__(self, settings: Optional[WorkerSettings] = None, redis_client: Optional[redis.Redis] = None):
        self.settings = settings or get_settings()
        self._redis = redis_client
        self._memory_retries: dict = {}

        if self._redis is None:
            try:
                self._redis = redis.Redis(
                    host=self.settings.REDIS_HOST,
                    port=self.settings.REDIS_PORT,
                    password=self.settings.REDIS_PASSWORD or None,
                    db=self.settings.REDIS_DB,
                    decode_responses=True,
                    socket_timeout=2.0
                )
                self._redis.ping()
            except Exception:
                self._redis = None

    def _get_key(self, job_id: str) -> str:
        return f"{self.settings.RETRY_KEY_PREFIX}{job_id}"

    def get_attempts(self, job_id: str) -> int:
        """Returns the current number of failed attempts recorded for the job."""
        key = self._get_key(job_id)
        if self._redis is not None:
            try:
                val = self._redis.get(key)
                return int(val) if val else 0
            except Exception as e:
                logger.warning("Error fetching retry count from Redis: %s", e)

        return self._memory_retries.get(key, 0)

    def record_failure(self, job_id: str, error_msg: str = "") -> Tuple[bool, float, int]:
        """
        Increments failed attempt count and computes retry eligibility and backoff delay.
        Returns: (should_retry: bool, backoff_delay_seconds: float, current_attempt: int)
        """
        key = self._get_key(job_id)
        current_attempt = 1

        if self._redis is not None:
            try:
                current_attempt = self._redis.incr(key)
                self._redis.expire(key, 86400)  # 24h expiration
            except Exception as e:
                logger.warning("Redis error recording failure for %s: %s", job_id, e)
                current_attempt = self._memory_retries.get(key, 0) + 1
                self._memory_retries[key] = current_attempt
        else:
            current_attempt = self._memory_retries.get(key, 0) + 1
            self._memory_retries[key] = current_attempt

        if current_attempt > self.settings.MAX_RETRIES:
            logger.error(
                "Job %s exceeded max retries (%d/%d). Error: %s. Sending to Dead Letter Queue (DLQ).",
                job_id,
                current_attempt,
                self.settings.MAX_RETRIES,
                error_msg
            )
            return False, 0.0, current_attempt

        # Exponential backoff formula: initial * (multiplier ** (attempt - 1))
        delay = self.settings.INITIAL_RETRY_DELAY_SECONDS * (
            self.settings.BACKOFF_MULTIPLIER ** (current_attempt - 1)
        )

        logger.warning(
            "Job %s failed (attempt %d/%d). Retrying after %.2fs backoff. Error: %s",
            job_id,
            current_attempt,
            self.settings.MAX_RETRIES,
            delay,
            error_msg
        )
        return True, delay, current_attempt

    def reset_retries(self, job_id: str) -> None:
        """Resets the retry counter upon successful job completion."""
        key = self._get_key(job_id)
        if self._redis is not None:
            try:
                self._redis.delete(key)
                return
            except Exception as e:
                logger.warning("Error resetting retry key in Redis: %s", e)

        if key in self._memory_retries:
            del self._memory_retries[key]
