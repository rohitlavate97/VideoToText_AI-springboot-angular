"""Subtitle generation and export models."""
from enum import Enum
from typing import Any, Dict, List, Optional, Union
from pydantic import BaseModel, ConfigDict, Field, field_validator


class SubtitleFormat(str, Enum):
    """Supported subtitle export formats."""
    SRT = "srt"
    VTT = "vtt"
    TXT = "txt"
    JSON = "json"

    @classmethod
    def from_str(cls, value: str) -> "SubtitleFormat":
        if not value:
            return cls.SRT
        clean = value.lower().strip()
        for member in cls:
            if member.value == clean:
                return member
        raise ValueError(f"Unsupported subtitle format: '{value}'. Expected one of: {[m.value for m in cls]}")

    @property
    def file_extension(self) -> str:
        return f".{self.value}"

    @property
    def mime_type(self) -> str:
        if self == SubtitleFormat.SRT:
            return "application/x-subrip"
        elif self == SubtitleFormat.VTT:
            return "text/vtt"
        elif self == SubtitleFormat.TXT:
            return "text/plain"
        elif self == SubtitleFormat.JSON:
            return "application/json"
        return "text/plain"


class SubtitleEntry(BaseModel):
    """A single normalized subtitle cue or transcript unit."""
    model_config = ConfigDict(populate_by_name=True)

    index: int = Field(1, description="Sequential cue number (1-based)")
    start_time: float = Field(0.0, alias="startTime", description="Start timestamp in seconds")
    end_time: float = Field(0.0, alias="endTime", description="End timestamp in seconds")
    text: str = Field(..., description="Subtitle text content")
    speaker: Optional[str] = Field(None, description="Raw speaker label (e.g. SPEAKER_00)")
    speaker_name: Optional[str] = Field(None, alias="speakerName", description="Human-readable speaker display name")
    confidence: float = Field(1.0, ge=0.0, le=1.0, description="Confidence score")
    language: Optional[str] = Field("en", description="Language code")

    @field_validator("start_time", "end_time", mode="before")
    @classmethod
    def clamp_non_negative(cls, v: Any) -> float:
        return max(0.0, float(v))


class SubtitleGenerationRequest(BaseModel):
    """Request payload for subtitle generation."""
    segments: List[Any] = Field(default_factory=list)
    format: SubtitleFormat = SubtitleFormat.SRT
    language: str = "en"
    include_speakers: bool = False
    speaker_map: Optional[Dict[str, str]] = Field(default_factory=dict, description="Map of speaker label -> display name")
    max_chars_per_line: int = Field(42, description="Max character length per subtitle line")
    max_lines: int = Field(2, description="Max lines per subtitle block")


class SubtitleResult(BaseModel):
    """Result of subtitle generation."""
    content: str = Field(..., description="Formatted subtitle text or JSON payload")
    format: SubtitleFormat = Field(..., description="Exported format")
    language: str = Field("en", description="Language of content")
    total_cues: int = Field(0, description="Total number of subtitle cues")
    duration_seconds: float = Field(0.0, description="Total span duration in seconds")
    includes_speakers: bool = Field(False, description="Whether speaker labels are rendered")
    file_extension: str = Field(".srt", description="Standard file extension")
    mime_type: str = Field("text/plain", description="MIME content type")
