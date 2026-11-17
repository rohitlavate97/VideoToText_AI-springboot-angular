import pytest
from uuid import uuid4

from app.providers.base_provider import TranscriptionSegment
from app.summarization.models import (
    SummaryLength,
    SummaryFormat,
    PrivacyMode,
    ActionItem,
    Topic,
    Chapter,
    TranscriptSegmentInput,
    InsightRequest,
    InsightResult,
)
from app.summarization.provider import BaseSummaryProvider, MockSummaryProvider
from app.summarization.engine import (
    LocalHeuristicSummaryEngine,
    HostedLLMSummaryEngine,
    SummaryEngineFactory,
)
from app.summarization.insights import KeyPointExtractor, ActionItemExtractor, TopicIdentifier
from app.summarization.pipeline import SummaryPipeline


@pytest.fixture
def sample_segments():
    return [
        TranscriptSegmentInput(
            segment_index=0,
            start_time=0.0,
            end_time=4.5,
            speaker_label="Alice",
            text="Good morning team, welcome to the quarterly architecture review."
        ),
        TranscriptSegmentInput(
            segment_index=1,
            start_time=4.5,
            end_time=10.0,
            speaker_label="Alice",
            text="Today our primary goal is to address our transcription pipeline performance."
        ),
        TranscriptSegmentInput(
            segment_index=2,
            start_time=10.0,
            end_time=16.0,
            speaker_label="Bob",
            text="We need to optimize the database query latency before the production release."
        ),
        TranscriptSegmentInput(
            segment_index=3,
            start_time=16.0,
            end_time=22.0,
            speaker_label="Bob",
            text="I will benchmark the PostgreSQL connection pool and share results by tomorrow."
        ),
        TranscriptSegmentInput(
            segment_index=4,
            start_time=22.0,
            end_time=28.5,
            speaker_label="Alice",
            text="Make sure to verify that the caching layer handles cache stampedes."
        ),
        TranscriptSegmentInput(
            segment_index=5,
            start_time=28.5,
            end_time=35.0,
            speaker_label="Alice",
            text="Thank you all, let us execute these milestones and reconvene next Monday."
        ),
    ]


def test_summary_engine_factory():
    mock_eng = SummaryEngineFactory.create_engine("mock")
    assert isinstance(mock_eng, MockSummaryProvider)
    assert mock_eng.provider_name == "mock"

    local_eng = SummaryEngineFactory.create_engine("local")
    assert isinstance(local_eng, LocalHeuristicSummaryEngine)
    assert local_eng.provider_name == "local"

    default_eng = SummaryEngineFactory.create_engine()
    assert isinstance(default_eng, LocalHeuristicSummaryEngine)


def test_privacy_enforcement_local_only():
    with pytest.raises(PermissionError) as exc_info:
        SummaryEngineFactory.create_engine(
            provider_name="openai",
            privacy_mode=PrivacyMode.LOCAL_ONLY
        )
    assert "blocked under LOCAL_ONLY privacy mode" in str(exc_info.value)

    with pytest.raises(PermissionError):
        BaseSummaryProvider.enforce_privacy("cloud", PrivacyMode.LOCAL_ONLY)


def test_privacy_mode_cloud_allowed():
    eng = SummaryEngineFactory.create_engine(
        provider_name="openai",
        privacy_mode=PrivacyMode.CLOUD_ALLOWED,
        model_name="gpt-4o-mini"
    )
    assert isinstance(eng, HostedLLMSummaryEngine)
    assert eng.provider_name == "hosted"
    assert eng.model_name == "gpt-4o-mini"


def test_mock_summary_provider_lengths_and_formats(sample_segments):
    provider = MockSummaryProvider()
    text = " ".join(s.text for s in sample_segments)

    # Short
    short_sum = provider.generate_summary(text, sample_segments, SummaryLength.SHORT, SummaryFormat.PARAGRAPH)
    assert "[MOCK SUMMARY - SHORT]" in short_sum

    # Detailed
    det_sum = provider.generate_summary(text, sample_segments, SummaryLength.DETAILED, SummaryFormat.PARAGRAPH)
    assert "Section 1:" in det_sum
    assert "Section 3:" in det_sum

    # Bullets
    bullet_sum = provider.generate_summary(text, sample_segments, SummaryLength.MEDIUM, SummaryFormat.BULLETS)
    assert bullet_sum.startswith("• ")

    # Executive
    exec_sum = provider.generate_summary(text, sample_segments, SummaryLength.MEDIUM, SummaryFormat.EXECUTIVE)
    assert "Executive Brief:" in exec_sum


def test_local_heuristic_summary_generation(sample_segments):
    engine = LocalHeuristicSummaryEngine()
    text = " ".join(s.text for s in sample_segments)

    # Short summary
    short_sum = engine.generate_summary(text, sample_segments, SummaryLength.SHORT, SummaryFormat.PARAGRAPH)
    assert len(short_sum) > 20
    assert not short_sum.startswith("• ")

    # Detailed summary
    det_sum = engine.generate_summary(text, sample_segments, SummaryLength.DETAILED, SummaryFormat.PARAGRAPH)
    assert len(det_sum) >= len(short_sum)

    # Bullets format
    bullet_sum = engine.generate_summary(text, sample_segments, SummaryLength.MEDIUM, SummaryFormat.BULLETS)
    assert bullet_sum.startswith("• ")

    # Executive format
    exec_sum = engine.generate_summary(text, sample_segments, SummaryLength.MEDIUM, SummaryFormat.EXECUTIVE)
    assert "Executive Summary:" in exec_sum
    assert "Strategic Highlights:" in exec_sum


def test_key_point_extraction(sample_segments):
    points = KeyPointExtractor.extract("", sample_segments, max_points=4)
    assert len(points) >= 2
    assert len(points) <= 4
    for p in points:
        assert p.endswith(".")


def test_action_item_extraction_with_speaker_and_timestamps(sample_segments):
    items = ActionItemExtractor.extract("", sample_segments)
    assert len(items) >= 2

    tasks_lower = [it.task.lower() for it in items]
    assert any("need to optimize" in t for t in tasks_lower)
    assert any("will benchmark" in t for t in tasks_lower)

    # Verify speaker attribution and timestamps
    bob_items = [it for it in items if it.assignee == "Bob"]
    assert len(bob_items) >= 1
    assert bob_items[0].timestamp >= 10.0


def test_topic_identification_and_timestamps(sample_segments):
    topics = TopicIdentifier.identify("", sample_segments)
    assert len(topics) >= 1

    for top in topics:
        assert 0.0 <= top.relevance <= 1.0
        assert top.start_time is not None
        assert top.start_time >= 0.0


def test_chapter_generation_continuity(sample_segments):
    engine = LocalHeuristicSummaryEngine()
    chapters = engine.generate_chapters(sample_segments)
    assert len(chapters) >= 1

    for ch in chapters:
        assert ch.start_time >= 0.0
        assert ch.end_time > ch.start_time
        assert len(ch.title) > 3
        assert len(ch.summary) > 5

    # Chronological check
    if len(chapters) > 1:
        for i in range(len(chapters) - 1):
            assert chapters[i].start_time <= chapters[i+1].start_time


def test_summary_pipeline_end_to_end(sample_segments):
    pipeline = SummaryPipeline(provider_name="local")
    job_id = uuid4()

    # Mix of TranscriptionSegment and TranscriptSegmentInput
    mixed_segments = [
        TranscriptionSegment(
            id=0,
            text="Welcome to the system demo.",
            start_time=0.0,
            end_time=3.0,
            words=[],
            speaker="Lead"
        ),
        sample_segments[2], # Bob: We need to optimize...
        sample_segments[3], # Bob: I will benchmark...
    ]

    result = pipeline.process(
        segments=mixed_segments,
        job_id=job_id,
        summary_length=SummaryLength.MEDIUM,
        summary_format=SummaryFormat.PARAGRAPH
    )

    assert isinstance(result, InsightResult)
    assert len(result.summary_text) > 10
    assert len(result.key_points) > 0
    assert len(result.action_items) > 0
    assert len(result.topics) > 0
    assert len(result.chapters) > 0
    assert result.llm_provider == "local"
