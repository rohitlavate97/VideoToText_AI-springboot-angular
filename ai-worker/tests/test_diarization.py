import tempfile
import os
import pytest
from app.diarization.associator import SpeakerAssociator
from app.diarization.engine import AcousticDiarizationEngine, DiarizationEngineFactory, PyAnnoteDiarizationEngine
from app.diarization.metrics import DiarizationMetrics
from app.diarization.models import DiarizationTurn, SpeakerProfile
from app.diarization.reconciler import SpeakerReconciler
from app.models.job import TranscriptionJobRequest
from app.processors.audio_extractor import AudioExtractor
from app.processors.transcription_processor import VideoTranscriptionProcessor
from app.providers.base_provider import TranscriptionSegment, WordTimestamp
from app.providers.mock_provider import MockASRProvider


@pytest.fixture
def sample_wav():
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        path = f.name
    extractor = AudioExtractor()
    extractor._generate_fallback_wav(path, duration_seconds=12)
    yield path
    if os.path.exists(path):
        os.remove(path)


def test_acoustic_diarization_engine(sample_wav):
    engine = AcousticDiarizationEngine()
    result = engine.diarize(sample_wav, num_speakers=2)

    assert len(result.turns) >= 2
    assert len(result.speakers) == 2
    for turn in result.turns:
        assert turn.end_time > turn.start_time
        assert turn.speaker_label in ["SPEAKER_00", "SPEAKER_01"]
        assert turn.embedding is not None
        assert len(turn.embedding) == 128


def test_diarization_factory_fallback():
    # When no HF token is present, factory creates AcousticDiarizationEngine
    engine = DiarizationEngineFactory.create_engine("auto")
    assert isinstance(engine, (AcousticDiarizationEngine, PyAnnoteDiarizationEngine))


def test_speaker_associator_temporal_overlap():
    # Segments: [0.0 - 4.0s], [5.0 - 9.0s], [10.0 - 12.0s]
    seg1 = TranscriptionSegment(id=0, start_time=0.5, end_time=3.5, text="First utterance")
    seg2 = TranscriptionSegment(id=1, start_time=5.5, end_time=8.5, text="Second utterance")
    seg3 = TranscriptionSegment(id=2, start_time=15.0, end_time=18.0, text="Isolated utterance")

    # Turns: [0.0 - 4.0s] -> SPEAKER_00, [5.0 - 10.0s] -> SPEAKER_01
    turns = [
        DiarizationTurn(start_time=0.0, end_time=4.0, speaker_label="SPEAKER_00"),
        DiarizationTurn(start_time=5.0, end_time=10.0, speaker_label="SPEAKER_01")
    ]

    associated, profiles = SpeakerAssociator.associate_segments([seg1, seg2, seg3], turns)

    assert len(associated) == 3
    assert associated[0].speaker == "SPEAKER_00"
    assert associated[1].speaker == "SPEAKER_01"
    # seg3 is at 15.0s, far outside turns -> UNKNOWN
    assert associated[2].speaker == "UNKNOWN"

    assert len(profiles) == 3
    labels = {p.speaker_label for p in profiles}
    assert labels == {"SPEAKER_00", "SPEAKER_01", "UNKNOWN"}


def test_speaker_reconciler_cross_chunk_similarity():
    reconciler = SpeakerReconciler(similarity_threshold=0.70)

    # Chunk 0 has Speaker A (represented by vector_A)
    vector_a = [1.0] + [0.0] * 127
    turn0 = DiarizationTurn(start_time=0.0, end_time=5.0, speaker_label="local_spk_0", embedding=vector_a)
    prof0 = SpeakerProfile(speaker_label="local_spk_0", display_name="Speaker 1", total_speaking_time_ms=5000, embedding=vector_a)
    seg0 = TranscriptionSegment(id=0, start_time=0.0, end_time=5.0, text="Hello chunk 0", speaker="local_spk_0")

    t_rec0, s_rec0, g_profs0 = reconciler.reconcile_chunk(0, [turn0], [seg0], [prof0])
    assert s_rec0[0].speaker == "SPEAKER_00"
    assert len(g_profs0) == 1

    # Chunk 1 has local_spk_X with highly similar vector (0.95 similarity) -> should map to SPEAKER_00
    vector_a_similar = [0.98] + [0.02] + [0.0] * 126
    turn1 = DiarizationTurn(start_time=5.0, end_time=10.0, speaker_label="local_spk_X", embedding=vector_a_similar)
    prof1 = SpeakerProfile(speaker_label="local_spk_X", display_name="Speaker X", total_speaking_time_ms=5000, embedding=vector_a_similar)
    seg1 = TranscriptionSegment(id=1, start_time=5.0, end_time=10.0, text="Hello chunk 1", speaker="local_spk_X")

    t_rec1, s_rec1, g_profs1 = reconciler.reconcile_chunk(1, [turn1], [seg1], [prof1])
    assert s_rec1[0].speaker == "SPEAKER_00"
    assert len(g_profs1) == 1
    assert g_profs1[0].total_speaking_time_ms == 10000

    # Chunk 2 has an orthogonal vector -> should map to new SPEAKER_01
    vector_b = [0.0, 1.0] + [0.0] * 126
    turn2 = DiarizationTurn(start_time=10.0, end_time=15.0, speaker_label="local_spk_Y", embedding=vector_b)
    prof2 = SpeakerProfile(speaker_label="local_spk_Y", display_name="Speaker Y", total_speaking_time_ms=5000, embedding=vector_b)
    seg2 = TranscriptionSegment(id=2, start_time=10.0, end_time=15.0, text="New speaker chunk 2", speaker="local_spk_Y")

    t_rec2, s_rec2, g_profs2 = reconciler.reconcile_chunk(2, [turn2], [seg2], [prof2])
    assert s_rec2[0].speaker == "SPEAKER_01"
    assert len(g_profs2) == 2


def test_diarization_der_metric():
    ref = [
        DiarizationTurn(start_time=0.0, end_time=5.0, speaker_label="SPEAKER_00"),
        DiarizationTurn(start_time=5.0, end_time=10.0, speaker_label="SPEAKER_01")
    ]
    # Perfect match -> DER 0.0
    hyp_perfect = [
        DiarizationTurn(start_time=0.0, end_time=5.0, speaker_label="SPEAKER_00"),
        DiarizationTurn(start_time=5.0, end_time=10.0, speaker_label="SPEAKER_01")
    ]
    assert DiarizationMetrics.calculate_der(ref, hyp_perfect) == 0.0

    # Inverted labels -> high DER
    hyp_inverted = [
        DiarizationTurn(start_time=0.0, end_time=5.0, speaker_label="SPEAKER_01"),
        DiarizationTurn(start_time=5.0, end_time=10.0, speaker_label="SPEAKER_00")
    ]
    assert DiarizationMetrics.calculate_der(ref, hyp_inverted) == 1.0


def test_transcription_processor_with_diarization():
    mock_provider = MockASRProvider(model_size="base")
    processor = VideoTranscriptionProcessor(provider=mock_provider)

    job = TranscriptionJobRequest(
        job_id="00000000-0000-0000-0000-000000000001",
        video_id="00000000-0000-0000-0000-000000000002",
        user_id="00000000-0000-0000-0000-000000000003",
        s3_key="videos/test.mp4",
        enable_diarization=True
    )

    result = processor.process_job(job)
    assert result.status == "COMPLETED"
    assert result.speakers_count >= 1
