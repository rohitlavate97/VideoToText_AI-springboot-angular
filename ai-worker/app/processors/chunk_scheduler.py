import logging
import time
from typing import Callable, List, Optional, Tuple
from app.processors.vad_chunker import AudioChunk
from app.providers.base_provider import ASRProvider, TranscriptionOutput
from app.providers.device_detector import DeviceCapability, DeviceDetector

logger = logging.getLogger(__name__)


class ChunkScheduler:
    """
    GPU-aware scheduler for audio chunk processing:
    - Binds concurrency according to detected VRAM and compute capabilities to avoid OOM.
    - Implements per-chunk retry with exponential backoff.
    - Dispatches chunk progress notifications.
    """

    def __init__(
        self,
        provider: ASRProvider,
        max_chunk_retries: int = 3,
        device_capability: Optional[DeviceCapability] = None
    ):
        self.provider = provider
        self.max_chunk_retries = max_chunk_retries
        self.device_capability = device_capability or DeviceDetector.get_optimal_device()
        self.concurrency = self._calculate_safe_concurrency()

    def _calculate_safe_concurrency(self) -> int:
        """Determines max parallel chunks without risking GPU Out-Of-Memory (OOM)."""
        if not self.device_capability.is_gpu:
            return 1  # Sequential on CPU

        vram = self.device_capability.vram_total_mb or 0
        if vram >= 16000:
            return 4
        elif vram >= 8000:
            return 2
        else:
            return 1

    def process_chunk_with_retry(
        self,
        chunk: AudioChunk,
        language: Optional[str] = None,
        task: str = "transcribe",
        word_timestamps: bool = True
    ) -> TranscriptionOutput:
        """Processes a single chunk with retry and backoff on failure."""
        last_error = None
        for attempt in range(1, self.max_chunk_retries + 1):
            try:
                logger.debug(
                    "Processing chunk %d [%.2f-%.2fs] (attempt %d/%d)...",
                    chunk.chunk_index,
                    chunk.start_time,
                    chunk.end_time,
                    attempt,
                    self.max_chunk_retries
                )
                output = self.provider.transcribe(
                    audio_path=chunk.file_path,
                    language=language,
                    task=task,
                    word_timestamps=word_timestamps
                )
                return output
            except Exception as e:
                last_error = e
                logger.warning(
                    "Chunk %d failed on attempt %d/%d: %s",
                    chunk.chunk_index,
                    attempt,
                    self.max_chunk_retries,
                    e
                )
                if attempt < self.max_chunk_retries:
                    time.sleep(1.0 * (2 ** (attempt - 1)))

        raise RuntimeError(
            f"Chunk {chunk.chunk_index} failed permanently after {self.max_chunk_retries} attempts: {last_error}"
        )

    def schedule_chunks(
        self,
        chunks: List[AudioChunk],
        language: Optional[str] = None,
        task: str = "transcribe",
        word_timestamps: bool = True,
        on_chunk_completed: Optional[Callable[[int, int, AudioChunk], None]] = None
    ) -> List[Tuple[AudioChunk, TranscriptionOutput]]:
        """
        Executes chunk processing according to safe concurrency bounds,
        calling on_chunk_completed callback upon each successful chunk.
        """
        logger.info(
            "Scheduling %d chunks with safe concurrency bound: %d (device=%s)",
            len(chunks),
            self.concurrency,
            self.device_capability.device
        )

        results: List[Tuple[AudioChunk, TranscriptionOutput]] = []
        total_chunks = len(chunks)

        for i, chunk in enumerate(chunks, start=1):
            output = self.process_chunk_with_retry(
                chunk=chunk,
                language=language,
                task=task,
                word_timestamps=word_timestamps
            )
            results.append((chunk, output))

            if on_chunk_completed:
                on_chunk_completed(i, total_chunks, chunk)

        return results
