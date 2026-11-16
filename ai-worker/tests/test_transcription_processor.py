import uuid
from unittest.mock import MagicMock
from app.messaging.publisher import EventPublisher
from app.models.event import ProcessingStage
from app.models.job import TranscriptionJobRequest
from app.processors.transcription_processor import VideoTranscriptionProcessor
from app.providers.mock_provider import MockASRProvider


def test_transcription_processor_full_pipeline():
    mock_publisher = MagicMock(spec=EventPublisher)
    mock_provider = MockASRProvider()

    processor = VideoTranscriptionProcessor(
        publisher=mock_publisher,
        provider=mock_provider
    )

    job_id = uuid.uuid4()
    video_id = uuid.uuid4()
    user_id = uuid.uuid4()

    job = TranscriptionJobRequest(
        job_id=job_id,
        video_id=video_id,
        user_id=user_id,
        s3_key=f"videos/{user_id}/{video_id}.mp4",
        language="en",
        task="transcribe",
        engine="mock",
        model="small",
        enable_diarization=True,
        target_languages=["hi"],
        generate_summary=True
    )

    result = processor.process_job(job)

    # 1. Verify result status & metadata
    assert result.status == "COMPLETED"
    assert result.job_id == job_id
    assert result.video_id == video_id
    assert result.detected_language == "en"
    assert result.duration_seconds > 0.0
    assert result.segments_count > 0
    assert result.speakers_count == 2  # diarization enabled

    # 2. Verify progress events were emitted for all required stages
    published_events = [call.args[0] for call in mock_publisher.publish_event.call_args_list]
    stages_emitted = [e.stage for e in published_events]

    assert ProcessingStage.AUDIO_EXTRACTION in stages_emitted
    assert ProcessingStage.VAD_CHUNKING in stages_emitted
    assert ProcessingStage.TRANSCRIBING in stages_emitted
    assert ProcessingStage.DIARIZING in stages_emitted
    assert ProcessingStage.TRANSLATING in stages_emitted
    assert ProcessingStage.SUMMARIZING in stages_emitted
    assert ProcessingStage.COMPLETED in stages_emitted

    # Final event must be 100%
    assert published_events[-1].progress_percent == 100.0

    # 3. Verify final result was published
    mock_publisher.publish_result.assert_called_once_with(result)
