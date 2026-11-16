import uuid
from app.consumers.idempotency import IdempotencyManager


def test_idempotency_in_memory_lifecycle():
    manager = IdempotencyManager(redis_client=None)
    job_id = str(uuid.uuid4())

    # Initially not completed
    assert manager.is_already_completed(job_id) is False

    # First acquisition should succeed
    assert manager.acquire_lock(job_id) is True

    # Concurrent acquisition while in-progress should fail
    assert manager.acquire_lock(job_id) is False

    # Still not completed
    assert manager.is_already_completed(job_id) is False

    # Mark completed
    manager.mark_completed(job_id)

    # Now is_already_completed should be True
    assert manager.is_already_completed(job_id) is True

    # Lock acquisition on completed job must fail
    assert manager.acquire_lock(job_id) is False


def test_idempotency_lock_release():
    manager = IdempotencyManager(redis_client=None)
    job_id = str(uuid.uuid4())

    assert manager.acquire_lock(job_id) is True
    assert manager.acquire_lock(job_id) is False

    # Release lock
    manager.release_lock(job_id)

    # Should be acquirable again
    assert manager.acquire_lock(job_id) is True
