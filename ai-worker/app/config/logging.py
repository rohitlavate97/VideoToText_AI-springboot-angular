import logging
import sys
from typing import Optional


class CorrelationLogFormatter(logging.Formatter):
    """Custom formatter that automatically includes job_id or worker context if present."""

    def format(self, record: logging.LogRecord) -> str:
        if not hasattr(record, "job_id"):
            record.job_id = "-"
        return super().format(record)


def setup_logging(level: str = "INFO", log_format: Optional[str] = None) -> None:
    """Configures centralized logging for the AI Worker process."""
    if not log_format:
        log_format = (
            "[%(asctime)s] [%(levelname)s] [%(name)s] [job_id:%(job_id)s] %(message)s"
        )

    root_logger = logging.getLogger()
    numeric_level = getattr(logging, level.upper(), logging.INFO)
    root_logger.setLevel(numeric_level)

    # Remove existing handlers to avoid duplicates
    for handler in root_logger.handlers[:]:
        root_logger.removeHandler(handler)

    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(numeric_level)
    formatter = CorrelationLogFormatter(log_format, datefmt="%Y-%m-%d %H:%M:%S")
    console_handler.setFormatter(formatter)
    root_logger.addHandler(console_handler)

    # Suppress verbose third-party loggers
    logging.getLogger("pika").setLevel(logging.WARNING)
    logging.getLogger("boto3").setLevel(logging.WARNING)
    logging.getLogger("botocore").setLevel(logging.WARNING)
    logging.getLogger("urllib3").setLevel(logging.WARNING)
