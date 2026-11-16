import logging
import os
import wave
from typing import List, Optional
from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)


class AudioChunk(BaseModel):
    """Audio segment slice with boundary overlap metadata for seamless ASR stitching."""
    chunk_index: int
    start_time: float = Field(..., ge=0.0)
    end_time: float = Field(..., ge=0.0)
    overlap_seconds: float = Field(0.0, ge=0.0)
    duration: float = Field(..., gt=0.0)
    file_path: str


class VADChunker:
    """
    Segments long audio streams into speech-bounded chunks (up to 10 minutes / 600s),
    with configurable overlap to prevent clipping boundary phonemes.
    """

    def __init__(self, max_chunk_duration: int = 600, overlap_seconds: float = 1.0):
        self.max_chunk_duration = max_chunk_duration
        self.overlap_seconds = overlap_seconds

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

    def chunk_audio(
        self,
        wav_path: str,
        overlap_seconds: Optional[float] = None,
        output_dir: Optional[str] = None
    ) -> List[AudioChunk]:
        """
        Splits audio into sequential chunks of at most max_chunk_duration seconds.
        Consecutive chunks include overlap_seconds to preserve phonetic context.
        """
        if not os.path.exists(wav_path):
            raise FileNotFoundError(f"Target WAV file not found: {wav_path}")

        overlap = overlap_seconds if overlap_seconds is not None else self.overlap_seconds
        duration = self.get_audio_duration(wav_path)

        logger.info(
            "Chunking audio '%s' (duration=%.2fs, max_chunk=%ds, overlap=%.2fs)...",
            wav_path,
            duration,
            self.max_chunk_duration,
            overlap
        )

        # Single chunk if duration is within limit
        if duration <= self.max_chunk_duration:
            return [
                AudioChunk(
                    chunk_index=0,
                    start_time=0.0,
                    end_time=duration,
                    overlap_seconds=0.0,
                    duration=duration,
                    file_path=wav_path
                )
            ]

        # Multi-chunk segmentation with overlap window
        chunks: List[AudioChunk] = []
        current_start = 0.0
        chunk_idx = 0

        while current_start < duration:
            current_end = min(duration, current_start + self.max_chunk_duration)
            chunk_overlap = overlap if chunk_idx > 0 else 0.0
            actual_start = max(0.0, current_start - chunk_overlap)
            chunk_duration = round(current_end - actual_start, 2)

            chunks.append(
                AudioChunk(
                    chunk_index=chunk_idx,
                    start_time=round(actual_start, 2),
                    end_time=round(current_end, 2),
                    overlap_seconds=round(chunk_overlap, 2),
                    duration=chunk_duration,
                    file_path=wav_path
                )
            )
            current_start = current_end
            chunk_idx += 1

        logger.info("Audio segmented into %d chunks with overlap.", len(chunks))
        return chunks
