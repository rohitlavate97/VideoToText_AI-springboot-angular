from abc import ABC, abstractmethod
from typing import List, Optional, Tuple
from pydantic import BaseModel, Field


class WordTimestamp(BaseModel):
    """Word-level alignment timestamp."""
    word: str
    start_time: float = Field(..., ge=0.0)
    end_time: float = Field(..., ge=0.0)
    probability: float = Field(1.0, ge=0.0, le=1.0)


class TranscriptionSegment(BaseModel):
    """Segment-level transcription result with timestamps and optional word alignments."""
    id: int
    start_time: float = Field(..., ge=0.0)
    end_time: float = Field(..., ge=0.0)
    text: str
    words: List[WordTimestamp] = Field(default_factory=list)
    confidence: float = Field(1.0, ge=0.0, le=1.0)
    language: Optional[str] = None
    speaker: Optional[str] = None


class TranscriptionOutput(BaseModel):
    """Complete structured output from an ASR transcription execution."""
    text: str
    segments: List[TranscriptionSegment]
    detected_language: str = "en"
    language_probability: float = 1.0
    duration_seconds: float = 0.0
    provider_name: str
    model_name: str


class ASRProvider(ABC):
    """Abstract Base Class for Automated Speech Recognition (ASR) engine providers."""

    @abstractmethod
    def load_model(self, model_size: str = "small", device: str = "auto", compute_type: str = "int8_float16") -> None:
        """Loads and prepares the model weights on the target compute device."""
        pass

    @abstractmethod
    def transcribe(
        self,
        audio_path: str,
        language: Optional[str] = None,
        task: str = "transcribe",
        word_timestamps: bool = True,
        initial_prompt: Optional[str] = None
    ) -> TranscriptionOutput:
        """Performs speech-to-text transcription on the supplied 16kHz mono WAV audio file."""
        pass

    @abstractmethod
    def detect_language(self, audio_path: str) -> Tuple[str, float]:
        """Auto-detects spoken language from the initial audio window. Returns (language_code, probability)."""
        pass

    @abstractmethod
    def get_device_info(self) -> dict:
        """Returns hardware execution metadata (device, compute type, VRAM/RAM)."""
        pass
