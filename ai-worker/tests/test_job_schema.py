import uuid
import pytest
from pydantic import ValidationError
from app.models.job import TranscriptionJobRequest, TranscriptionJobResult
from app.models.event import ProcessingEvent, ProcessingStage


def test_transcription_job_request_from_camel_case():
    job_id = uuid.uuid4()
    video_id = uuid.uuid4()
    user_id = uuid.uuid4()

    payload = {
        "jobId": str(job_id),
        "videoId": str(video_id),
        "userId": str(user_id),
        "s3Key": f"videos/{user_id}/{video_id}.mp4",
        "language": "hi",
        "task": "transcribe",
        "engine": "faster-whisper",
        "model": "medium",
        "computeType": "float16",
        "enableDiarization": True,
        "maxSpeakers": 4,
        "targetLanguages": ["en", "mr"],
        "generateSummary": True
    }

    req = TranscriptionJobRequest.model_validate(payload)
    assert req.job_id == job_id
    assert req.video_id == video_id
    assert req.user_id == user_id
    assert req.language == "hi"
    assert req.enable_diarization is True
    assert req.max_speakers == 4
    assert req.target_languages == ["en", "mr"]
    assert req.generate_summary is True


def test_transcription_job_request_defaults():
    job_id = uuid.uuid4()
    video_id = uuid.uuid4()
    user_id = uuid.uuid4()

    payload = {
        "jobId": str(job_id),
        "videoId": str(video_id),
        "userId": str(user_id),
        "s3Key": "videos/sample.mp4"
    }

    req = TranscriptionJobRequest.model_validate(payload)
    assert req.language == "auto"
    assert req.task == "transcribe"
    assert req.engine == "faster-whisper"
    assert req.model == "small"
    assert req.compute_type == "int8_float16"
    assert req.enable_diarization is False
    assert req.max_speakers is None
    assert req.target_languages == []
    assert req.generate_summary is False
    assert req.created_at is not None


def test_processing_event_validation():
    job_id = uuid.uuid4()
    event = ProcessingEvent(
        job_id=job_id,
        stage=ProcessingStage.TRANSCRIBING,
        progress_percent=55.5,
        completed_chunks=11,
        total_chunks=20,
        message="Transcribing chunk 11 of 20"
    )

    data = event.model_dump(by_alias=True)
    assert data["jobId"] == job_id
    assert data["stage"] == "TRANSCRIBING"
    assert data["progressPercent"] == 55.5
    assert data["completedChunks"] == 11
    assert data["totalChunks"] == 20
    assert data["retryAttempt"] == 0
    assert data["maxRetries"] == 0
    assert data["errorMessage"] is None

    # Invalid percent > 100 should raise ValidationError
    with pytest.raises(ValidationError):
        ProcessingEvent(
            job_id=job_id,
            stage=ProcessingStage.TRANSCRIBING,
            progress_percent=105.0,
            message="Over 100 percent"
        )


def test_processing_event_with_retry_and_error():
    job_id = uuid.uuid4()
    event = ProcessingEvent(
        job_id=job_id,
        stage=ProcessingStage.RETRYING,
        progress_percent=40.0,
        completed_chunks=2,
        total_chunks=5,
        retry_attempt=2,
        max_retries=3,
        error_message="CUDA out of memory during audio chunk 3",
        message="Processing attempt 2/3 failed. Retrying..."
    )

    data = event.model_dump(by_alias=True)
    assert data["jobId"] == job_id
    assert data["stage"] == "RETRYING"
    assert data["retryAttempt"] == 2
    assert data["maxRetries"] == 3
    assert data["errorMessage"] == "CUDA out of memory during audio chunk 3"
    assert "Retrying" in data["message"]


def test_processing_stages_enumeration():
    expected_stages = [
        "QUEUED", "AUDIO_EXTRACTION", "VAD_CHUNKING", "TRANSCRIBING",
        "DIARIZING", "TRANSLATING", "SUMMARIZING", "RETRYING",
        "COMPLETED", "FAILED", "CANCELLED"
    ]
    for stage_name in expected_stages:
        assert ProcessingStage(stage_name) is not None


def test_transcription_job_result():
    job_id = uuid.uuid4()
    video_id = uuid.uuid4()

    result = TranscriptionJobResult(
        job_id=job_id,
        video_id=video_id,
        status="COMPLETED",
        duration_seconds=360.5,
        detected_language="en",
        segments_count=52,
        speakers_count=2
    )

    data = result.model_dump(by_alias=True)
    assert data["jobId"] == job_id
    assert data["status"] == "COMPLETED"
    assert data["durationSeconds"] == 360.5
    assert data["detectedLanguage"] == "en"
    assert data["segmentsCount"] == 52
    assert data["speakersCount"] == 2
    assert data["errorMessage"] is None
