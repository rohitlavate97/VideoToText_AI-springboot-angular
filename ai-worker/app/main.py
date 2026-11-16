import sys
import logging
from app.config.settings import get_settings

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("ai-worker")


def main():
    settings = get_settings()
    logger.info("==================================================")
    logger.info("Starting VideoToText AI Worker")
    logger.info("Worker Name: %s", settings.WORKER_NAME)
    logger.info("Version: %s", settings.WORKER_VERSION)
    logger.info("Environment: %s", settings.ENVIRONMENT)
    logger.info("ASR Engine: %s (model: %s, device: %s)",
                settings.ASR_DEFAULT_ENGINE,
                settings.ASR_DEFAULT_MODEL,
                settings.ASR_DEVICE)
    logger.info("RabbitMQ Host: %s:%d (vhost: %s)",
                settings.RABBITMQ_HOST,
                settings.RABBITMQ_PORT,
                settings.RABBITMQ_VHOST)
    logger.info("==================================================")
    logger.info("AI Worker initialization complete. Ready for phase integration.")


if __name__ == "__main__":
    main()
