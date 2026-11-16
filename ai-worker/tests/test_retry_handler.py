import uuid
from app.consumers.retry_handler import RetryHandler


def test_retry_handler_exponential_backoff():
    handler = RetryHandler(redis_client=None)
    job_id = str(uuid.uuid4())

    # Initially 0 attempts
    assert handler.get_attempts(job_id) == 0

    # Attempt 1
    should_retry, delay, attempt = handler.record_failure(job_id, "Connection timed out")
    assert should_retry is True
    assert attempt == 1
    assert delay == 2.0  # 2.0 * (2.0 ** 0)

    # Attempt 2
    should_retry, delay, attempt = handler.record_failure(job_id, "Resource busy")
    assert should_retry is True
    assert attempt == 2
    assert delay == 4.0  # 2.0 * (2.0 ** 1)

    # Attempt 3
    should_retry, delay, attempt = handler.record_failure(job_id, "Network glitch")
    assert should_retry is True
    assert attempt == 3
    assert delay == 8.0  # 2.0 * (2.0 ** 2)

    # Attempt 4 (Exceeds max retries of 3)
    should_retry, delay, attempt = handler.record_failure(job_id, "Fatal out of memory")
    assert should_retry is False
    assert attempt == 4
    assert delay == 0.0


def test_retry_handler_reset():
    handler = RetryHandler(redis_client=None)
    job_id = str(uuid.uuid4())

    handler.record_failure(job_id, "First error")
    assert handler.get_attempts(job_id) == 1

    handler.reset_retries(job_id)
    assert handler.get_attempts(job_id) == 0
