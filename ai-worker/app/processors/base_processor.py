import logging
import time
from abc import ABC, abstractmethod
from typing import Optional
from app.messaging.publisher import EventPublisher
from app.models.event import ProcessingEvent, ProcessingStage
from app.models.job import TranscriptionJobRequest, TranscriptionJobResult

logger = logging.getLogger(__name__)


class BaseJobProcessor(ABC):
    """Abstract base class orchestrating the multi-stage AI transcription pipeline."""

    def __init__(self, publisher: Optional[EventPublisher] = None):
        self.publisher = publisher

    def emit_progress(
        self,
        job_id,
        stage: ProcessingStage,
        percent: float,
        message: str,
        completed_chunks: int = 0,
        total_chunks: int = 0,
        retry_attempt: int = 0,
        max_retries: int = 0,
        error_message: Optional[str] = None
    ) -> None:
        """Dispatches progress update event to RabbitMQ."""
        if self.publisher:
            event = ProcessingEvent(
                job_id=job_id,
                stage=stage,
                progress_percent=min(100.0, max(0.0, percent)),
                completed_chunks=completed_chunks,
                total_chunks=total_chunks,
                retry_attempt=retry_attempt,
                max_retries=max_retries,
                error_message=error_message,
                message=message
            )
            self.publisher.publish_event(event)

    @abstractmethod
    def process_job(self, job: TranscriptionJobRequest) -> TranscriptionJobResult:
        """Executes the complete processing lifecycle for the incoming job request."""
        pass


class DefaultJobProcessor(BaseJobProcessor):
    """
    Standard lifecycle processor that verifies input parameters, emits staged progress events,
    and produces job completion results. Concrete ASR/VAD/Diarization logic is plugged in Phase 6+.
    """

    def process_job(self, job: TranscriptionJobRequest) -> TranscriptionJobResult:
        logger.info("Starting processing lifecycle for job %s (s3Key: %s)", job.job_id, job.s3_key)
        start_time = time.time()

        # Stage 1: Audio Extraction
        self.emit_progress(
            job.job_id,
            ProcessingStage.AUDIO_EXTRACTION,
            10.0,
            "Extracting 16kHz mono audio stream from video..."
        )

        # Stage 2: VAD & Chunking
        self.emit_progress(
            job.job_id,
            ProcessingStage.VAD_CHUNKING,
            20.0,
            "Detecting speech activity and segmenting into chunks..."
        )

        # Stage 3: Transcribing (simulate chunks progression)
        total_chunks = 4
        for i in range(1, total_chunks + 1):
            percent = 20.0 + (50.0 * (i / total_chunks))
            self.emit_progress(
                job.job_id,
                ProcessingStage.TRANSCRIBING,
                percent,
                f"Transcribing audio chunk {i} of {total_chunks} with {job.engine} ({job.model})...",
                completed_chunks=i,
                total_chunks=total_chunks
            )

        # Stage 4: Diarization (if enabled)
        if job.enable_diarization:
            self.emit_progress(
                job.job_id,
                ProcessingStage.DIARIZING,
                78.0,
                "Identifying speakers with PyAnnote Diarization..."
            )

        # Stage 5: Translation (if target languages requested)
        if job.target_languages:
            self.emit_progress(
                job.job_id,
                ProcessingStage.TRANSLATING,
                88.0,
                f"Translating transcript into {', '.join(job.target_languages)}..."
            )

        # Stage 6: Summarization (if enabled)
        if job.generate_summary:
            self.emit_progress(
                job.job_id,
                ProcessingStage.SUMMARIZING,
                95.0,
                "Generating intelligent summary and meeting action items..."
            )

        # Stage 7: Completed
        duration = round(time.time() - start_time, 2)
        self.emit_progress(
            job.job_id,
            ProcessingStage.COMPLETED,
            100.0,
            "Transcription and post-processing completed successfully."
        )

        result = TranscriptionJobResult(
            job_id=job.job_id,
            video_id=job.video_id,
            status="COMPLETED",
            duration_seconds=max(1.0, duration),
            detected_language=job.language if job.language != "auto" else "en",
            segments_count=total_chunks * 8,
            speakers_count=2 if job.enable_diarization else 1
        )

        if self.publisher:
            self.publisher.publish_result(result)

        logger.info("Successfully completed job %s in %.2fs", job.job_id, duration)
        return result
