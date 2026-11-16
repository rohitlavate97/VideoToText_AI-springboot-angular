import os
import time
from typing import List, Optional, Tuple
from app.providers.base_provider import (
    ASRProvider,
    TranscriptionOutput,
    TranscriptionSegment,
    WordTimestamp
)


class MockASRProvider(ASRProvider):
    """
    Deterministic ASR Provider used for integration testing, unit testing,
    and environments where native CUDA / C-extensions are restricted.
    """

    def __init__(self, model_size: str = "small", device: str = "cpu", compute_type: str = "int8"):
        self.model_size = model_size
        self.device = device
        self.compute_type = compute_type
        self.is_loaded = False
        self.mock_text = (
            "Welcome to VideoToText AI platform. Today we are demonstrating automated "
            "speech recognition with timestamped segments and word alignment."
        )

    def load_model(self, model_size: Optional[str] = None, device: Optional[str] = None, compute_type: Optional[str] = None) -> None:
        if model_size:
            self.model_size = model_size
        if device:
            self.device = device
        if compute_type:
            self.compute_type = compute_type
        self.is_loaded = True

    def transcribe(
        self,
        audio_path: str,
        language: Optional[str] = None,
        task: str = "transcribe",
        word_timestamps: bool = True,
        initial_prompt: Optional[str] = None
    ) -> TranscriptionOutput:
        if not self.is_loaded:
            self.load_model()

        detected_lang = language if (language and language != "auto") else "en"

        # Generate realistic segments and word timestamps
        words_list = self.mock_text.split()
        words_per_segment = 6
        segments: List[TranscriptionSegment] = []
        current_time = 0.0

        for seg_idx in range(0, len(words_list), words_per_segment):
            seg_words = words_list[seg_idx : seg_idx + words_per_segment]
            seg_word_objs: List[WordTimestamp] = []
            seg_start = current_time

            for w in seg_words:
                w_start = round(current_time, 3)
                current_time += 0.45
                w_end = round(current_time, 3)
                current_time += 0.05
                if word_timestamps:
                    seg_word_objs.append(
                        WordTimestamp(
                            word=w,
                            start_time=w_start,
                            end_time=w_end,
                            probability=0.98
                        )
                    )

            seg_end = round(current_time, 3)
            segments.append(
                TranscriptionSegment(
                    id=len(segments),
                    start_time=seg_start,
                    end_time=seg_end,
                    text=" ".join(seg_words),
                    words=seg_word_objs,
                    confidence=0.97,
                    language=detected_lang
                )
            )
            current_time += 0.2

        total_duration = round(current_time, 2)
        return TranscriptionOutput(
            text=self.mock_text,
            segments=segments,
            detected_language=detected_lang,
            language_probability=0.99,
            duration_seconds=total_duration,
            provider_name="mock-provider",
            model_name=self.model_size
        )

    def detect_language(self, audio_path: str) -> Tuple[str, float]:
        return "en", 0.99

    def get_device_info(self) -> dict:
        return {
            "provider": "mock-provider",
            "model_size": self.model_size,
            "device": self.device,
            "compute_type": self.compute_type
        }
