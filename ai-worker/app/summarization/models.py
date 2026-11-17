from enum import Enum
from typing import List, Optional
from uuid import UUID
from pydantic import BaseModel, Field


class SummaryLength(str, Enum):
    """Supported summary length variations."""
    SHORT = "short"        # 1-2 concise paragraphs or key summary (~50-80 words)
    MEDIUM = "medium"      # Standard summary (~150-250 words)
    DETAILED = "detailed"  # Comprehensive structured multi-paragraph summary (~400+ words)

    @classmethod
    def from_str(cls, value: Optional[str]) -> "SummaryLength":
        if not value:
            return cls.MEDIUM
        cleaned = value.lower().strip()
        for member in cls:
            if member.value == cleaned:
                return member
        return cls.MEDIUM


class SummaryFormat(str, Enum):
    """Output format for the generated summary."""
    PARAGRAPH = "paragraph"
    BULLETS = "bullets"
    EXECUTIVE = "executive"

    @classmethod
    def from_str(cls, value: Optional[str]) -> "SummaryFormat":
        if not value:
            return cls.PARAGRAPH
        cleaned = value.lower().strip()
        for member in cls:
            if member.value == cleaned:
                return member
        return cls.PARAGRAPH


class PrivacyMode(str, Enum):
    """Privacy enforcement level for LLM operations."""
    LOCAL_ONLY = "local_only"        # Zero cloud/external API calls, strictly local NLP
    CLOUD_ALLOWED = "cloud_allowed"  # External hosted LLMs (OpenAI, Anthropic) permitted
    HYBRID = "hybrid"                # Local fallback if external is unreachable

    @classmethod
    def from_str(cls, value: Optional[str]) -> "PrivacyMode":
        if not value:
            return cls.LOCAL_ONLY
        cleaned = value.lower().strip()
        for member in cls:
            if member.value == cleaned:
                return member
        return cls.LOCAL_ONLY


class ActionItem(BaseModel):
    """Actionable commitment, task, or follow-up item extracted from transcript."""
    task: str = Field(..., description="Actionable item description or obligation")
    assignee: Optional[str] = Field(None, description="Identified speaker or assignee responsible")
    timestamp: Optional[float] = Field(None, ge=0.0, description="Approximate video timestamp in seconds")
    completed: bool = Field(False, description="Completion toggle state")


class Topic(BaseModel):
    """Key topic or theme detected in the conversation."""
    name: str = Field(..., description="Topic label or phrase")
    relevance: float = Field(..., ge=0.0, le=1.0, description="Relative relevance score 0.0-1.0")
    start_time: Optional[float] = Field(None, ge=0.0, description="First appearance timestamp in seconds")


class Chapter(BaseModel):
    """Chronological video chapter heading with synchronized timestamps."""
    title: str = Field(..., description="Chapter heading title")
    start_time: float = Field(..., ge=0.0, description="Chapter start time in seconds")
    end_time: float = Field(..., ge=0.0, description="Chapter end time in seconds")
    summary: str = Field(..., description="Concise synopsis of discussion during this chapter")


class TranscriptSegmentInput(BaseModel):
    """Standardized segment input for summarization and insights processing."""
    segment_index: int = Field(0, description="Index of segment")
    start_time: float = Field(0.0, ge=0.0, description="Start timestamp in seconds")
    end_time: float = Field(0.0, ge=0.0, description="End timestamp in seconds")
    speaker_label: Optional[str] = Field(None, description="Speaker attribution")
    text: str = Field(..., description="Transcribed text content")


class InsightRequest(BaseModel):
    """Request payload for summarization and intelligent insight extraction."""
    job_id: Optional[UUID] = Field(None, description="Associated transcription job UUID")
    transcript_text: Optional[str] = Field(None, description="Raw concatenated transcript text if available")
    segments: List[TranscriptSegmentInput] = Field(default_factory=list, description="Chronological transcript segments")
    summary_length: SummaryLength = Field(SummaryLength.MEDIUM, description="Target summary length")
    summary_format: SummaryFormat = Field(SummaryFormat.PARAGRAPH, description="Target summary presentation format")
    extract_key_points: bool = Field(True, description="Whether to extract key points")
    extract_action_items: bool = Field(True, description="Whether to extract action items")
    extract_topics: bool = Field(True, description="Whether to extract important topics")
    generate_chapters: bool = Field(True, description="Whether to generate chronological chapters")
    provider: Optional[str] = Field(None, description="Requested summary provider override")


class InsightResult(BaseModel):
    """Structured insight output containing summary, key points, action items, topics, and chapters."""
    summary_text: str = Field(..., description="Synthesized summary text")
    summary_length: str = Field(..., description="Summary length category")
    summary_format: str = Field(..., description="Summary presentation format")
    key_points: List[str] = Field(default_factory=list, description="Extracted bulleted key points")
    action_items: List[ActionItem] = Field(default_factory=list, description="Extracted action items")
    topics: List[Topic] = Field(default_factory=list, description="Extracted topics with timestamps")
    chapters: List[Chapter] = Field(default_factory=list, description="Generated chapter headings and timestamps")
    llm_provider: str = Field(..., description="Provider utilized (e.g. local, mock, openai)")
    llm_model: str = Field(..., description="Model identifier utilized")
