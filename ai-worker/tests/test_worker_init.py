import unittest
from app.config.settings import WorkerSettings, get_settings


class TestWorkerInit(unittest.TestCase):
    """Test worker configuration and initialization defaults."""

    def test_default_settings(self):
        settings = get_settings()
        self.assertEqual(settings.WORKER_NAME, "videototext-ai-worker")
        self.assertEqual(settings.WORKER_VERSION, "0.1.0")
        self.assertEqual(settings.ASR_DEFAULT_ENGINE, "faster-whisper")
        self.assertEqual(settings.ASR_DEFAULT_MODEL, "small")
        self.assertEqual(settings.CHUNK_DURATION_SECONDS, 600)
        self.assertEqual(settings.MAX_VIDEO_DURATION_MINUTES, 240)

    def test_custom_settings(self):
        custom = WorkerSettings(
            WORKER_NAME="custom-worker",
            ASR_DEFAULT_MODEL="large-v3-turbo",
            CHUNK_DURATION_SECONDS=300
        )
        self.assertEqual(custom.WORKER_NAME, "custom-worker")
        self.assertEqual(custom.ASR_DEFAULT_MODEL, "large-v3-turbo")
        self.assertEqual(custom.CHUNK_DURATION_SECONDS, 300)


if __name__ == "__main__":
    unittest.main()
