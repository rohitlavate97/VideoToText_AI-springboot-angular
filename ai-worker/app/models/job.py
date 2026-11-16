from datetime import datetime, timezone
from typing import List, Optional
from uuid import UUID
from pydantic import BaseModel, ConfigDict, Field


class TranscriptionJobRequest(BaseModel):
    """Schema representing incoming job requests from transcription.requests queue."""

    model_config = ConfigDict(
        populate_by_name=True,
        extra="ignore"
    )

    job_id: UUID = Field(..., alias="jobId")
    video_id: UUID = Field(..., alias="videoId")
    user_id: UUID = Field(..., alias="userId")
    s3_key: str = Field(..., alias="s3Key")
    language: Optional[str] = Field("auto", alias="language")
    task: str = Field("transcribe", alias="task")
    engine: str = Field("faster-whisper", alias="engine")
    model: str = Field("small", alias="model")
    compute_type: str = Field("int8_float16", alias="computeType")
    enable_diarization: bool = Field(False, alias="enableDiarization")
    max_speakers: Optional[int] = Field(None, alias="maxSpeakers")
    target_languages: List[str] = Field(default_factory=list, alias="targetLanguages")
    generate_summary: bool = Field(False, alias="generateSummary")
    created_at: Optional[datetime] = Field(default_factory=lambda: datetime.now(timezone.utc), alias="createdAt")


class TranscriptionJobResult(BaseModel):
    """Schema representing the completed job payload published to transcription.results queue."""

    model_config = ConfigDict(
        populate_by_name=True,
        extra="ignore"
    )

    job_id: UUID = Field(..., alias="jobId")
    video_id: UUID = Field(..., alias="videoId")
    status: str = Field(..., alias="status")  # COMPLETED | FAILED
    duration_seconds: float = Field(0.0, alias="durationSeconds")
    detected_language: Optional[str] = Field(None, alias="detectedLanguage")
    segments_count: int = Field(0, alias="segmentsCount")
    speakers_count: int = Field(0, alias="speakersCount")
    error_message: Optional[str] = Field(None, alias="errorMessage")
    completed_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc), alias="completedAt")
