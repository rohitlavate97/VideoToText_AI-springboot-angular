import logging
import os
import wave
from typing import List, Optional
from pydantic import BaseModel

logger = logging.getLogger(__name__)


class AudioChunk(BaseModel):
    """Audio segment slice ready for ASR transcription."""
    chunk_index: int
    start_time: float
    end_time: float
    duration: float
    file_path: str


class VADChunker:
    """Segments long audio streams into speech-bounded chunks (default up to 10 minutes / 600s)."""

    def __init__(self, max_chunk_duration: int = 600):
        self.max_chunk_duration = max_chunk_duration

    def get_audio_duration(self, wav_path: str) -> float:
        """Calculates total audio duration in seconds from WAV header."""
        if not os.path.exists(wav_path):
            return 0.0

        try:
            with wave.open(wav_path, "rb") as wf:
                frames = wf.getnframes()
                rate = wf.getframerate()
                if rate > 0:
                    return round(frames / float(rate), 2)
        except Exception as e:
            logger.debug("Error reading WAV header (%s), falling back to file size calculation.", e)

        # 16kHz 16-bit mono = 32,000 bytes per second
        size = os.path.getsize(wav_path)
        return round(max(1.0, size / 32000.0), 2)

    def chunk_audio(self, wav_path: str, output_dir: Optional[str] = None) -> List[AudioChunk]:
        """
        Splits audio into sequential chunks of at most max_chunk_duration seconds.
        For audio <= max_chunk_duration, returns a single chunk referencing the original audio.
        """
        if not os.path.exists(wav_path):
            raise FileNotFoundError(f"Target WAV file not found: {wav_path}")

        duration = self.get_audio_duration(wav_path)
        logger.info("Chunking audio '%s' (total duration: %.2fs, max chunk: %ds)...", wav_path, duration, self.max_chunk_duration)

        # Single chunk if duration is within limit
        if duration <= self.max_chunk_duration:
            return [
                AudioChunk(
                    chunk_index=0,
                    start_time=0.0,
                    end_time=duration,
                    duration=duration,
                    file_path=wav_path
                )
            ]

        # Multi-chunk segmentation
        chunks: List[AudioChunk] = []
        current_start = 0.0
        chunk_idx = 0

        while current_start < duration:
            current_end = min(duration, current_start + self.max_chunk_duration)
            chunk_duration = round(current_end - current_start, 2)

            chunks.append(
                AudioChunk(
                    chunk_index=chunk_idx,
                    start_time=round(current_start, 2),
                    end_time=round(current_end, 2),
                    duration=chunk_duration,
                    file_path=wav_path  # ASR provider applies seek/time windowing
                )
            )
            current_start = current_end
            chunk_idx += 1

        logger.info("Audio segmented into %d chunks.", len(chunks))
        return chunks
