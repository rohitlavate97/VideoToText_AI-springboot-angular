from datetime import datetime, timezone
from enum import Enum
from uuid import UUID
from pydantic import BaseModel, ConfigDict, Field


class ProcessingStage(str, Enum):
    QUEUED = "QUEUED"
    AUDIO_EXTRACTION = "AUDIO_EXTRACTION"
    VAD_CHUNKING = "VAD_CHUNKING"
    TRANSCRIBING = "TRANSCRIBING"
    DIARIZING = "DIARIZING"
    TRANSLATING = "TRANSLATING"
    SUMMARIZING = "SUMMARIZING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"


class ProcessingEvent(BaseModel):
    """Schema representing live progress events published to processing.events queue."""

    model_config = ConfigDict(
        populate_by_name=True,
        extra="ignore"
    )

    job_id: UUID = Field(..., alias="jobId")
    stage: ProcessingStage = Field(..., alias="stage")
    progress_percent: float = Field(..., alias="progressPercent", ge=0.0, le=100.0)
    completed_chunks: int = Field(0, alias="completedChunks", ge=0)
    total_chunks: int = Field(0, alias="totalChunks", ge=0)
    message: str = Field(..., alias="message")
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc), alias="timestamp")
