from enum import Enum
from typing import List, Optional
from uuid import UUID
from pydantic import BaseModel, Field


class SupportedLanguage(str, Enum):
    """Primary supported translation languages."""
    ENGLISH = "en"
    HINDI = "hi"
    MARATHI = "mr"
    SPANISH = "es"
    FRENCH = "fr"
    GERMAN = "de"

    @classmethod
    def is_supported(cls, code: str) -> bool:
        if not code:
            return False
        clean = code.lower().strip()
        return any(clean == member.value for member in cls)


class TranslatedSegment(BaseModel):
    """
    Transcript segment translated to a target language with exact
    original start_time, end_time, and speaker identity strictly preserved.
    """
    segment_index: int = Field(..., description="Chronological zero-based index of segment")
    start_time: float = Field(..., ge=0.0, description="Start timestamp in seconds (preserved)")
    end_time: float = Field(..., ge=0.0, description="End timestamp in seconds (preserved)")
    speaker_label: Optional[str] = Field(None, description="Speaker attribution label (preserved)")
    source_text: str = Field(..., description="Original transcribed speech text")
    translated_text: str = Field(..., description="Translated text in target language")
    target_language: str = Field(..., description="Target ISO 639-1 language code")
    confidence: float = Field(1.0, ge=0.0, le=1.0, description="Translation quality / confidence score")


class TranslationQualityReport(BaseModel):
    """Evaluation metrics assessing translation quality and integrity."""
    length_ratio: float = Field(..., description="Target characters / source characters ratio")
    has_script_integrity: bool = Field(..., description="Whether target matches expected script (e.g. Devanagari)")
    is_non_empty: bool = Field(..., description="Whether translated text is non-empty")
    estimated_quality_score: float = Field(..., ge=0.0, le=1.0, description="Heuristic quality score 0.0-1.0")


class TranslationResult(BaseModel):
    """Aggregated translation result for a transcription job in a specific language."""
    job_id: UUID = Field(..., description="Transcription job UUID")
    source_language: str = Field(..., description="Original detected or specified source language")
    target_language: str = Field(..., description="Target translated language code")
    translation_engine: str = Field(..., description="Translation engine identifier")
    segments: List[TranslatedSegment] = Field(default_factory=list, description="Preserved translated segments")
    full_text: str = Field(..., description="Concatenated translated text")
    quality_score: float = Field(1.0, ge=0.0, le=1.0, description="Overall translation quality score")
