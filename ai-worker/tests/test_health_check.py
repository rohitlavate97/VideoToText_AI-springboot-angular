from unittest.mock import MagicMock, patch
from fastapi.testclient import TestClient
from app.health.checker import HealthChecker
from app.main import api_app
from app.models.health import ComponentHealth, ServiceStatus

client = TestClient(api_app)


def test_health_liveness_endpoint():
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "UP"
    assert "worker" in data


def test_info_endpoint():
    response = client.get("/info")
    assert response.status_code == 200
    data = response.json()
    assert data["default_engine"] == "faster-whisper"
    assert "rabbitmq_queue" in data


def test_health_checker_all_up():
    checker = HealthChecker()
    with patch.object(checker, "check_rabbitmq", return_value=ComponentHealth(status=ServiceStatus.UP, latency_ms=1.5)), \
         patch.object(checker, "check_redis", return_value=ComponentHealth(status=ServiceStatus.UP, latency_ms=0.8)), \
         patch.object(checker, "check_s3", return_value=ComponentHealth(status=ServiceStatus.UP, latency_ms=2.1)):

        report = checker.get_full_health(active_jobs=1)
        assert report.status == ServiceStatus.UP
        assert report.active_jobs == 1
        assert report.components["rabbitmq"].status == ServiceStatus.UP
        assert report.components["redis"].status == ServiceStatus.UP
        assert report.components["storage"].status == ServiceStatus.UP


def test_health_checker_rabbitmq_down_marks_worker_down():
    checker = HealthChecker()
    with patch.object(checker, "check_rabbitmq", return_value=ComponentHealth(status=ServiceStatus.DOWN, details={"error": "refused"})), \
         patch.object(checker, "check_redis", return_value=ComponentHealth(status=ServiceStatus.UP)), \
         patch.object(checker, "check_s3", return_value=ComponentHealth(status=ServiceStatus.UP)):

        report = checker.get_full_health(active_jobs=0)
        assert report.status == ServiceStatus.DOWN
