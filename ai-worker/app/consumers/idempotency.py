import json
import logging
import time
from typing import Optional
import redis
from app.config.settings import WorkerSettings, get_settings

logger = logging.getLogger(__name__)


class IdempotencyManager:
    """Provides distributed idempotency check and lock management using Redis or in-memory fallback."""

    def __init__(self, settings: Optional[WorkerSettings] = None, redis_client: Optional[redis.Redis] = None):
        self.settings = settings or get_settings()
        self._redis = redis_client
        self._memory_store: dict = {}  # fallback for in-memory / testing

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
                logger.info("Connected to Redis for idempotency management.")
            except Exception as e:
                logger.warning(
                    "Redis not reachable (%s). Falling back to local in-memory idempotency store.", e
                )
                self._redis = None

    def _get_key(self, job_id: str) -> str:
        return f"{self.settings.IDEMPOTENCY_KEY_PREFIX}{job_id}"

    def is_already_completed(self, job_id: str) -> bool:
        """Returns True if the job has already been marked as COMPLETED."""
        key = self._get_key(job_id)
        if self._redis is not None:
            try:
                val = self._redis.get(key)
                if val:
                    data = json.loads(val)
                    return data.get("status") == "COMPLETED"
            except Exception as e:
                logger.warning("Error checking Redis idempotency key: %s", e)

        entry = self._memory_store.get(key)
        return entry is not None and entry.get("status") == "COMPLETED"

    def acquire_lock(self, job_id: str, lock_timeout: int = 300) -> bool:
        """
        Attempts to acquire processing lock for the job.
        Returns True if acquired (first execution or stale lock expired).
        """
        key = self._get_key(job_id)
        now = time.time()
        payload = json.dumps({"status": "IN_PROGRESS", "started_at": now})

        if self._redis is not None:
            try:
                # SET key val NX EX lock_timeout
                acquired = self._redis.set(key, payload, nx=True, ex=lock_timeout)
                if acquired:
                    return True
                # Check if current value is IN_PROGRESS
                val = self._redis.get(key)
                if val:
                    data = json.loads(val)
                    if data.get("status") == "COMPLETED":
                        return False
                return bool(acquired)
            except Exception as e:
                logger.warning("Redis error acquiring lock for job %s: %s", job_id, e)

        # Fallback in-memory lock
        if key in self._memory_store:
            data = self._memory_store[key]
            if data.get("status") == "COMPLETED":
                return False
            # Check if previous in-progress lock expired
            if now - data.get("started_at", 0) < lock_timeout:
                return False

        self._memory_store[key] = {"status": "IN_PROGRESS", "started_at": now}
        return True

    def mark_completed(self, job_id: str) -> None:
        """Marks the job as COMPLETED and preserves the record for IDEMPOTENCY_TTL_SECONDS."""
        key = self._get_key(job_id)
        payload = json.dumps({"status": "COMPLETED", "completed_at": time.time()})

        if self._redis is not None:
            try:
                self._redis.set(key, payload, ex=self.settings.IDEMPOTENCY_TTL_SECONDS)
                return
            except Exception as e:
                logger.warning("Error marking job %s completed in Redis: %s", job_id, e)

        self._memory_store[key] = {"status": "COMPLETED", "completed_at": time.time()}

    def mark_failed(self, job_id: str) -> None:
        """Clears or marks lock as FAILED so retries can proceed."""
        self.release_lock(job_id)

    def release_lock(self, job_id: str) -> None:
        """Releases the in-progress lock."""
        key = self._get_key(job_id)
        if self._redis is not None:
            try:
                val = self._redis.get(key)
                if val:
                    data = json.loads(val)
                    if data.get("status") == "IN_PROGRESS":
                        self._redis.delete(key)
                return
            except Exception as e:
                logger.warning("Error releasing lock in Redis for job %s: %s", job_id, e)

        if key in self._memory_store and self._memory_store[key].get("status") == "IN_PROGRESS":
            del self._memory_store[key]
