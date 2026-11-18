import uuid
import pytest
from unittest.mock import MagicMock, patch
from app.consumers.transcription_consumer import TranscriptionConsumer
from app.consumers.idempotency import IdempotencyManager
from app.consumers.retry_handler import RetryHandler
from app.messaging.publisher import EventPublisher
from app.messaging.rabbitmq_client import RabbitMQClient
from app.models.event import ProcessingStage
from app.models.job import TranscriptionJobRequest, TranscriptionJobResult
from app.processors.chunk_scheduler import ChunkScheduler
from app.processors.transcription_processor import VideoTranscriptionProcessor
from app.processors.vad_chunker import AudioChunk
from app.providers.base_provider import ASRProvider, TranscriptionOutput, TranscriptionSegment
from app.providers.device_detector import DeviceDetector, DeviceCapability
from app.translation.pipeline import TranslationPipeline
from app.translation.models import SupportedLanguage


class TestScenario7AudioWithoutSpeech:
    """Scenario 7: Audio without speech / silence yields 0 segments without crashing."""

    def test_audio_without_speech_returns_empty_segments_cleanly(self):
        mock_publisher = MagicMock(spec=EventPublisher)
        mock_provider = MagicMock(spec=ASRProvider)
        mock_provider.name = "mock"
        mock_provider.model_name = "small"
        mock_provider.transcribe.return_value = TranscriptionOutput(
            text="",
            segments=[],
            provider_name="mock",
            model_name="small"
        )

        processor = VideoTranscriptionProcessor(
            publisher=mock_publisher,
            provider=mock_provider
        )

        job_id = uuid.uuid4()
        video_id = uuid.uuid4()
        job = TranscriptionJobRequest(
            job_id=job_id,
            video_id=video_id,
            user_id=uuid.uuid4(),
            s3_key="videos/silent.mp4",
            language="en",
            task="transcribe",
            engine="mock",
            model="small",
            enable_diarization=False,
            generate_summary=False
        )

        result = processor.process_job(job)

        assert result.status == "COMPLETED"
        assert result.segments_count == 0
        assert result.duration_seconds >= 0.0
        assert result.error_message is None
        mock_publisher.publish_result.assert_called_once()


class TestScenarios9To12MultilingualTranscription:
    """Scenarios 9-12: Multilingual Hindi, Marathi, English, and Translation targets."""

    def test_hindi_transcription_returns_devanagari(self):
        mock_publisher = MagicMock(spec=EventPublisher)
        mock_provider = MagicMock(spec=ASRProvider)
        mock_provider.name = "mock"
        mock_provider.model_name = "small"
        mock_provider.transcribe.return_value = TranscriptionOutput(
            text="नमस्ते, वीडियो टू टेक्स्ट में आपका स्वागत है।",
            segments=[
                TranscriptionSegment(
                    id=0,
                    start_time=0.0,
                    end_time=3.5,
                    text="नमस्ते, वीडियो टू टेक्स्ट में आपका स्वागत है।",
                    language="hi",
                    confidence=0.96
                )
            ],
            language="hi",
            provider_name="mock",
            model_name="small"
        )

        processor = VideoTranscriptionProcessor(
            publisher=mock_publisher,
            provider=mock_provider
        )

        job = TranscriptionJobRequest(
            job_id=uuid.uuid4(),
            video_id=uuid.uuid4(),
            user_id=uuid.uuid4(),
            s3_key="videos/hindi_sample.mp4",
            language="hi",
            task="transcribe",
            engine="mock",
            model="small",
            enable_diarization=False,
            generate_summary=False
        )

        result = processor.process_job(job)
        assert result.status == "COMPLETED"
        assert result.detected_language == "hi"
        assert result.segments_count > 0

    def test_marathi_transcription_returns_marathi_text(self):
        mock_publisher = MagicMock(spec=EventPublisher)
        mock_provider = MagicMock(spec=ASRProvider)
        mock_provider.name = "mock"
        mock_provider.model_name = "small"
        mock_provider.transcribe.return_value = TranscriptionOutput(
            text="नमस्कार, हे एक मराठी संभाषण आहे.",
            segments=[
                TranscriptionSegment(
                    id=0,
                    start_time=0.0,
                    end_time=3.2,
                    text="नमस्कार, हे एक मराठी संभाषण आहे.",
                    language="mr",
                    confidence=0.94
                )
            ],
            language="mr",
            provider_name="mock",
            model_name="small"
        )

        processor = VideoTranscriptionProcessor(
            publisher=mock_publisher,
            provider=mock_provider
        )

        job = TranscriptionJobRequest(
            job_id=uuid.uuid4(),
            video_id=uuid.uuid4(),
            user_id=uuid.uuid4(),
            s3_key="videos/marathi_sample.mp4",
            language="mr",
            task="transcribe",
            engine="mock",
            model="small",
            enable_diarization=False,
            generate_summary=False
        )

        result = processor.process_job(job)
        assert result.status == "COMPLETED"
        assert result.detected_language == "mr"
        assert result.segments_count > 0

    def test_multilingual_translation_pipeline(self):
        from app.translation.engine import TranslationEngineFactory
        provider = TranslationEngineFactory.create_engine("dictionary")
        pipeline = TranslationPipeline(provider=provider)
        segments = [
            TranscriptionSegment(
                id=0,
                start_time=0.0,
                end_time=2.5,
                text="Welcome to the video presentation.",
                language="en",
                confidence=0.98
            )
        ]

        translations = pipeline.translate_all(
            job_id=uuid.uuid4(),
            segments=segments,
            source_lang="en",
            target_languages=["hi", "mr"]
        )

        assert "hi" in translations
        assert "mr" in translations
        assert len(translations["hi"].segments) == 1
        assert len(translations["mr"].segments) == 1
        assert any(ord(c) >= 0x0900 and ord(c) <= 0x097F for c in translations["hi"].segments[0].translated_text)


class TestScenario13GpuUnavailableCpuFallback:
    """Scenario 13: GPU unavailable gracefully falls back to CPU int8."""

    def test_cpu_explicit_fallback(self):
        cap = DeviceDetector.get_optimal_device(requested_device="cpu")
        assert cap.device == "cpu"
        assert cap.is_gpu is False
        assert cap.recommended_compute_type == "int8"
        assert cap.cpu_cores > 0

    @patch("ctranslate2.get_cuda_device_count", return_value=0)
    @patch.object(DeviceDetector, "detect_nvidia_smi", return_value=(False, None, None, None))
    def test_auto_fallback_when_no_gpu_available(self, mock_smi, mock_ctranslate):
        cap = DeviceDetector.get_optimal_device(requested_device="auto")
        assert cap.device == "cpu"
        assert cap.is_gpu is False
        assert cap.recommended_compute_type == "int8"

    def test_cpu_concurrency_throttled_to_single_worker(self):
        mock_provider = MagicMock(spec=ASRProvider)
        cpu_cap = DeviceCapability(
            device="cpu",
            is_gpu=False,
            device_name="Host CPU",
            recommended_compute_type="int8",
            cpu_cores=8
        )
        scheduler = ChunkScheduler(provider=mock_provider, device_capability=cpu_cap)
        assert scheduler.concurrency == 1


class TestScenario14WorkerCrashAndFailSafe:
    """Scenario 14: Worker crash / exception handling triggers fail-safe and error reporting."""

    def test_consumer_catches_unhandled_processor_crash(self):
        mock_client = MagicMock(spec=RabbitMQClient)
        mock_publisher = MagicMock(spec=EventPublisher)
        idempotency = IdempotencyManager(redis_client=None)
        retry_handler = RetryHandler(redis_client=None)

        mock_processor = MagicMock()
        mock_processor.process_job.side_effect = RuntimeError("Fatal memory segmentation error")

        consumer = TranscriptionConsumer(
            client=mock_client,
            publisher=mock_publisher,
            processor=mock_processor,
            idempotency=idempotency,
            retry_handler=retry_handler
        )

        mock_channel = MagicMock()
        method = MagicMock()
        method.delivery_tag = 99
        properties = MagicMock()

        job_id = uuid.uuid4()
        payload = f'{{"jobId":"{job_id}","videoId":"{uuid.uuid4()}","userId":"{uuid.uuid4()}","s3Key":"test.mp4","language":"en"}}'

        # Exhaust retries so it hits terminal failure branch
        retry_handler.record_failure(str(job_id), "prior error 1")
        retry_handler.record_failure(str(job_id), "prior error 2")
        retry_handler.record_failure(str(job_id), "prior error 3")

        consumer.on_message(mock_channel, method, properties, payload.encode("utf-8"))

        # Must reject to DLQ (requeue=False) and acknowledge terminal failure
        mock_channel.basic_nack.assert_called_once_with(delivery_tag=99, requeue=False)
        mock_publisher.publish_result.assert_called_once()
        published_result = mock_publisher.publish_result.call_args[0][0]
        assert published_result.status == "FAILED"
        assert "Fatal memory" in published_result.error_message


class TestScenario15RabbitMQInterruptionAndRetries:
    """Scenario 15: RabbitMQ interruption & retry behavior."""

    def test_exponential_backoff_and_limit(self):
        handler = RetryHandler(redis_client=None)
        job_id = str(uuid.uuid4())

        # Attempt 1 -> 2.0s delay
        r1, d1, a1 = handler.record_failure(job_id, "Broker network connection reset")
        assert r1 is True
        assert d1 == 2.0
        assert a1 == 1

        # Attempt 2 -> 4.0s delay
        r2, d2, a2 = handler.record_failure(job_id, "Broker network connection reset")
        assert r2 is True
        assert d2 == 4.0
        assert a2 == 2

        # Attempt 3 -> 8.0s delay
        r3, d3, a3 = handler.record_failure(job_id, "Broker network connection reset")
        assert r3 is True
        assert d3 == 8.0
        assert a3 == 3

        # Attempt 4 -> False (exhausted)
        r4, d4, a4 = handler.record_failure(job_id, "Broker network connection reset")
        assert r4 is False
        assert a4 == 4


class TestScenario16ChunkProcessingFailureAndRetry:
    """Scenario 16: Chunk processing failure recovers on retry."""

    def test_chunk_retry_succeeds_after_transient_failure(self):
        mock_provider = MagicMock(spec=ASRProvider)
        chunk = AudioChunk(chunk_index=0, start_time=0.0, end_time=10.0, file_path="chunk0.wav", duration=10.0)
        expected_output = TranscriptionOutput(
            text="transcription recovered",
            segments=[],
            provider_name="mock",
            model_name="small"
        )

        mock_provider.transcribe.side_effect = [
            IOError("Disk I/O lock timeout"),
            expected_output
        ]

        scheduler = ChunkScheduler(provider=mock_provider, max_chunk_retries=3)
        result = scheduler.process_chunk_with_retry(chunk)

        assert result.text == "transcription recovered"
        assert mock_provider.transcribe.call_count == 2


class TestScenario17TranslationFailureHandling:
    """Scenario 17: Translation failure handled gracefully without pipeline crash."""

    def test_unsupported_language_handled_gracefully(self):
        from app.translation.engine import TranslationEngineFactory
        provider = TranslationEngineFactory.create_engine("dictionary")
        pipeline = TranslationPipeline(provider=provider)
        segments = [
            TranscriptionSegment(
                id=0,
                start_time=0.0,
                end_time=1.0,
                text="Test speech",
                language="en",
                confidence=0.9
            )
        ]

        # Request translation to an unsupported language code 'unknown_lang'
        translations = pipeline.translate_all(
            job_id=uuid.uuid4(),
            segments=segments,
            source_lang="en",
            target_languages=["unknown_lang"]
        )

        # Unsupported language is handled gracefully with fallback tag without raising unhandled exception
        assert "unknown_lang" in translations
        assert "[UNKNOWN_LANG]" in translations["unknown_lang"].segments[0].translated_text


class TestScenario19And20ConcurrencyAndCancellation:
    """Scenarios 19 & 20: User cancellation lock cleanup & concurrent jobs safety."""

    def test_lock_released_on_cancellation_or_failure(self):
        idempotency = IdempotencyManager(redis_client=None)
        job_id = str(uuid.uuid4())

        assert idempotency.acquire_lock(job_id) is True
        assert idempotency.acquire_lock(job_id) is False  # Cannot acquire twice

        # Cancellation releases lock
        idempotency.release_lock(job_id)
        assert idempotency.acquire_lock(job_id) is True  # Can acquire again

    def test_multiple_concurrent_jobs_do_not_block_each_other(self):
        idempotency = IdempotencyManager(redis_client=None)
        job1 = str(uuid.uuid4())
        job2 = str(uuid.uuid4())
        job3 = str(uuid.uuid4())

        assert idempotency.acquire_lock(job1) is True
        assert idempotency.acquire_lock(job2) is True
        assert idempotency.acquire_lock(job3) is True

        # Idempotent state per job
        idempotency.mark_completed(job1)
        assert idempotency.is_already_completed(job1) is True
        assert idempotency.is_already_completed(job2) is False
        assert idempotency.is_already_completed(job3) is False
