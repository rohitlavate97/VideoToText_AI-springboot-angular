import os
import tempfile
from app.processors.audio_extractor import AudioExtractor
from app.processors.vad_chunker import VADChunker
from app.providers.base_provider import ASRProvider
from app.providers.device_detector import DeviceDetector
from app.providers.factory import ASRProviderFactory
from app.providers.mock_provider import MockASRProvider


def test_device_detector_cpu_override():
    cap = DeviceDetector.get_optimal_device(requested_device="cpu")
    assert cap.device == "cpu"
    assert cap.is_gpu is False
    assert cap.recommended_compute_type == "int8"
    assert cap.cpu_cores > 0


def test_device_detector_auto():
    cap = DeviceDetector.get_optimal_device(requested_device="auto")
    assert cap.device in ("cuda", "cpu")
    assert cap.recommended_compute_type in ("float16", "int8_float16", "int8")


def test_mock_provider_transcription_and_word_timestamps():
    provider = MockASRProvider(model_size="small", device="cpu", compute_type="int8")
    provider.load_model()

    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        dummy_path = f.name

    try:
        output = provider.transcribe(audio_path=dummy_path, language="en", word_timestamps=True)

        assert output.provider_name == "mock-provider"
        assert output.detected_language == "en"
        assert output.duration_seconds > 0
        assert len(output.segments) > 0

        # Verify word-level timestamps exist and are chronologically ordered
        first_segment = output.segments[0]
        assert len(first_segment.words) > 0

        prev_end = 0.0
        for word in first_segment.words:
            assert word.start_time >= prev_end
            assert word.end_time > word.start_time
            assert word.probability > 0.0
            prev_end = word.start_time
    finally:
        if os.path.exists(dummy_path):
            os.remove(dummy_path)


def test_mock_provider_language_detection():
    provider = MockASRProvider()
    lang, prob = provider.detect_language("dummy.wav")
    assert lang == "en"
    assert prob >= 0.9


def test_provider_factory_mock():
    provider = ASRProviderFactory.create_provider(engine="mock", model_size="base")
    assert isinstance(provider, ASRProvider)
    assert isinstance(provider, MockASRProvider)


def test_audio_extractor_fallback_generation():
    extractor = AudioExtractor(ffmpeg_path="nonexistent-ffmpeg-binary-path")
    with tempfile.TemporaryDirectory() as td:
        out_wav = os.path.join(td, "test.wav")
        created = extractor._generate_fallback_wav(out_wav, duration_seconds=3)
        assert os.path.exists(created)
        assert os.path.getsize(created) == 16000 * 2 * 3 + 44  # 16kHz * 2 bytes * 3s + 44-byte WAV header


def test_vad_chunker_single_chunk():
    chunker = VADChunker(max_chunk_duration=600)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        dummy_path = f.name

    try:
        extractor = AudioExtractor()
        extractor._generate_fallback_wav(dummy_path, duration_seconds=5)

        chunks = chunker.chunk_audio(dummy_path)
        assert len(chunks) == 1
        assert chunks[0].chunk_index == 0
        assert chunks[0].start_time == 0.0
        assert chunks[0].duration == 5.0
    finally:
        if os.path.exists(dummy_path):
            os.remove(dummy_path)


def test_vad_chunker_multi_chunk():
    # Set max chunk duration to 2 seconds to test chunk splitting
    chunker = VADChunker(max_chunk_duration=2)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        dummy_path = f.name

    try:
        extractor = AudioExtractor()
        extractor._generate_fallback_wav(dummy_path, duration_seconds=7)

        chunks = chunker.chunk_audio(dummy_path)
        assert len(chunks) == 4  # [0-2s], [2-4s], [4-6s], [6-7s]
        assert chunks[0].start_time == 0.0
        assert chunks[0].end_time == 2.0
        assert chunks[1].start_time == 2.0
        assert chunks[1].end_time == 4.0
        assert chunks[3].end_time == 7.0
    finally:
        if os.path.exists(dummy_path):
            os.remove(dummy_path)
