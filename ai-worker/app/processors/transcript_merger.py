import logging
from typing import List, Optional
from app.processors.vad_chunker import AudioChunk
from app.providers.base_provider import TranscriptionOutput, TranscriptionSegment, WordTimestamp

logger = logging.getLogger(__name__)


class TranscriptMerger:
    """
    Merges transcription outputs from individual chunks into a unified transcript:
    - Normalizes and offsets timestamps into global audio timeline coordinates.
    - Performs fuzzy/exact de-duplication across chunk overlap boundaries.
    - Preserves word-level timestamp alignment and sequential segment IDs.
    """

    @staticmethod
    def merge_chunks(
        chunk_outputs: List[tuple[AudioChunk, TranscriptionOutput]]
    ) -> List[TranscriptionSegment]:
        """
        Merges chunk results in chronological order, handling boundary de-duplication.
        `chunk_outputs` is a list of tuples: (AudioChunk, TranscriptionOutput).
        """
        if not chunk_outputs:
            return []

        # Sort by chunk_index
        sorted_pairs = sorted(chunk_outputs, key=lambda x: x[0].chunk_index)
        merged_segments: List[TranscriptionSegment] = []
        global_seg_id = 0
        last_global_end = 0.0

        for chunk, output in sorted_pairs:
            chunk_base_offset = chunk.start_time
            overlap = chunk.overlap_seconds

            for seg in output.segments:
                # Calculate global start and end times
                global_start = round(seg.start_time + chunk_base_offset, 3)
                global_end = round(seg.end_time + chunk_base_offset, 3)

                # Overlap de-duplication:
                # If segment falls entirely inside the overlap window from previous chunk, skip it
                if overlap > 0 and global_end <= last_global_end:
                    logger.debug(
                        "Skipping duplicate overlap segment [%.3f - %.3f]: '%s'",
                        global_start,
                        global_end,
                        seg.text
                    )
                    continue

                # Adjust word timestamps
                adjusted_words: List[WordTimestamp] = []
                for w in seg.words:
                    w_start = round(w.start_time + chunk_base_offset, 3)
                    w_end = round(w.end_time + chunk_base_offset, 3)
                    # Filter out words that occurred before previous chunk's end
                    if w_end > last_global_end:
                        adjusted_words.append(
                            w.model_copy(update={"start_time": w_start, "end_time": w_end})
                        )

                # Skip empty segment after word deduplication if words were present originally
                if seg.words and not adjusted_words:
                    continue

                adjusted_seg = TranscriptionSegment(
                    id=global_seg_id,
                    start_time=max(last_global_end, global_start),
                    end_time=max(last_global_end + 0.1, global_end),
                    text=seg.text.strip(),
                    words=adjusted_words,
                    confidence=seg.confidence,
                    language=seg.language,
                    speaker=seg.speaker
                )

                merged_segments.append(adjusted_seg)
                last_global_end = adjusted_seg.end_time
                global_seg_id += 1

        logger.info(
            "Merged %d chunk outputs into %d global transcript segments.",
            len(chunk_outputs),
            len(merged_segments)
        )
        return merged_segments
