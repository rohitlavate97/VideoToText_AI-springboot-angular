from typing import List, Optional
from uuid import UUID
from pydantic import BaseModel, Field


class DiarizationTurn(BaseModel):
    """A continuous audio segment attributed to a single speaker."""
    start_time: float = Field(..., ge=0.0)
    end_time: float = Field(..., ge=0.0)
    speaker_label: str = Field(..., min_length=1)
    confidence: float = Field(default=1.0, ge=0.0, le=1.0)
    embedding: Optional[List[float]] = None


class SpeakerProfile(BaseModel):
    """Metadata and cumulative telemetry for an identified speaker."""
    speaker_label: str
    display_name: str
    total_speaking_time_ms: int = 0
    segment_count: int = 0
    embedding: Optional[List[float]] = None


class DiarizationResult(BaseModel):
    """Aggregate result from diarization analysis."""
    turns: List[DiarizationTurn] = Field(default_factory=list)
    speakers: List[SpeakerProfile] = Field(default_factory=list)
    der: Optional[float] = None
