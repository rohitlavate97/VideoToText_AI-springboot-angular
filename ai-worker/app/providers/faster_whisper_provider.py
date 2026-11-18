import logging
import os
from typing import Optional, Tuple
from app.providers.base_provider import (
    ASRProvider,
    TranscriptionOutput,
    TranscriptionSegment,
    WordTimestamp
)

logger = logging.getLogger(__name__)

# Global model cache to avoid repeated weight loading and VRAM fragmentation across chunks
_GLOBAL_MODEL_CACHE = {}


class FasterWhisperProvider(ASRProvider):
    """
    ASR Provider implementation powered by faster-whisper (CTranslate2 backend).
    Optimized for high-throughput GPU inference and quantized CPU fallback.
    """

    def __init__(
        self,
        model_size: str = "small",
        device: str = "auto",
        compute_type: str = "int8_float16",
        fast_mode: bool = True
    ):
        self.model_size = model_size
        self.device = device
        self.compute_type = compute_type
        self.fast_mode = fast_mode
        self.model = None
        self._device_info = {}

    def load_model(self, model_size: Optional[str] = None, device: Optional[str] = None, compute_type: Optional[str] = None) -> None:
        """Loads faster-whisper model weights with thread-safe model instance caching."""
        if model_size:
            self.model_size = model_size
        if device:
            self.device = device
        if compute_type:
            self.compute_type = compute_type

        cache_key = (self.model_size, self.device, self.compute_type)
        if cache_key in _GLOBAL_MODEL_CACHE:
            logger.info("Reusing cached faster-whisper model: %s", cache_key)
            self.model = _GLOBAL_MODEL_CACHE[cache_key]
            self._device_info = {
                "provider": "faster-whisper",
                "model_size": self.model_size,
                "device": self.device,
                "compute_type": self.compute_type,
                "cached": True
            }
            return

        try:
            from faster_whisper import WhisperModel
        except ImportError as e:
            logger.error("faster-whisper is not available in the current environment: %s", e)
            raise RuntimeError(f"faster-whisper import failed: {e}")

        logger.info(
            "Loading faster-whisper model '%s' on device '%s' with compute_type '%s'...",
            self.model_size,
            self.device,
            self.compute_type
        )

        try:
            threads = max(1, min(os.cpu_count() or 4, 8))
            self.model = WhisperModel(
                self.model_size,
                device=self.device,
                compute_type=self.compute_type,
                cpu_threads=threads,
                num_workers=1
            )
            _GLOBAL_MODEL_CACHE[cache_key] = self.model
            self._device_info = {
                "provider": "faster-whisper",
                "model_size": self.model_size,
                "device": self.device,
                "compute_type": self.compute_type,
                "cached": False
            }
            logger.info("faster-whisper model '%s' loaded and cached successfully.", self.model_size)
        except Exception as err:
            logger.error("Failed to load faster-whisper model: %s", err)
            raise

    def transcribe(
        self,
        audio_path: str,
        language: Optional[str] = None,
        task: str = "transcribe",
        word_timestamps: bool = True,
        initial_prompt: Optional[str] = None,
        beam_size: Optional[int] = None
    ) -> TranscriptionOutput:
        """Transcribes audio file to timestamped text segments and word alignments."""
        if not self.model:
            self.load_model()

        if not os.path.exists(audio_path):
            raise FileNotFoundError(f"Audio file not found: {audio_path}")

        lang_param = None if (language in (None, "", "auto")) else language
        effective_beam = beam_size if beam_size is not None else (1 if self.fast_mode else 5)

        logger.info(
            "Transcribing '%s' [lang=%s, task=%s, word_timestamps=%s, beam=%d]...",
            audio_path,
            lang_param or "auto",
            task,
            word_timestamps,
            effective_beam
        )

        vad_params = dict(min_silence_duration_ms=400, threshold=0.4)

        segments_generator, info = self.model.transcribe(
            audio_path,
            language=lang_param,
            task=task,
            beam_size=effective_beam,
            word_timestamps=word_timestamps,
            vad_filter=True,
            vad_parameters=vad_params,
            condition_on_previous_text=False,
            initial_prompt=initial_prompt
        )

        segments = []
        full_text_parts = []
        segment_id = 0

        for seg in segments_generator:
            words = []
            if word_timestamps and hasattr(seg, "words") and seg.words:
                for w in seg.words:
                    words.append(
                        WordTimestamp(
                            word=w.word.strip(),
                            start_time=round(w.start, 3),
                            end_time=round(w.end, 3),
                            probability=round(getattr(w, "probability", 1.0), 3)
                        )
                    )

            cleaned_text = seg.text.strip()
            full_text_parts.append(cleaned_text)

            segments.append(
                TranscriptionSegment(
                    id=segment_id,
                    start_time=round(seg.start, 3),
                    end_time=round(seg.end, 3),
                    text=cleaned_text,
                    words=words,
                    confidence=round(getattr(seg, "avg_logprob", 0.0), 3),
                    language=info.language
                )
            )
            segment_id += 1

        total_text = " ".join(full_text_parts)
        duration = round(info.duration, 2) if hasattr(info, "duration") else 0.0

        return TranscriptionOutput(
            text=total_text,
            segments=segments,
            detected_language=info.language,
            language_probability=round(info.language_probability, 3),
            duration_seconds=duration,
            provider_name="faster-whisper",
            model_name=self.model_size
        )

    def detect_language(self, audio_path: str) -> Tuple[str, float]:
        """Detects language from first 30s audio sample."""
        if not self.model:
            self.load_model()

        if not os.path.exists(audio_path):
            raise FileNotFoundError(f"Audio file not found: {audio_path}")

        _, info = self.model.transcribe(audio_path, beam_size=1, vad_filter=False)
        return info.language, round(info.language_probability, 3)

    def get_device_info(self) -> dict:
        return self._device_info or {
            "provider": "faster-whisper",
            "model_size": self.model_size,
            "device": self.device,
            "compute_type": self.compute_type
        }
