"""AI Summarization and intelligent insights extraction module."""

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
from app.summarization.insights import (
    KeyPointExtractor,
    ActionItemExtractor,
    TopicIdentifier,
)
from app.summarization.engine import (
    LocalHeuristicSummaryEngine,
    HostedLLMSummaryEngine,
    SummaryEngineFactory,
)
from app.summarization.pipeline import SummaryPipeline

__all__ = [
    "SummaryLength",
    "SummaryFormat",
    "PrivacyMode",
    "ActionItem",
    "Topic",
    "Chapter",
    "TranscriptSegmentInput",
    "InsightRequest",
    "InsightResult",
    "BaseSummaryProvider",
    "MockSummaryProvider",
    "KeyPointExtractor",
    "ActionItemExtractor",
    "TopicIdentifier",
    "LocalHeuristicSummaryEngine",
    "HostedLLMSummaryEngine",
    "SummaryEngineFactory",
    "SummaryPipeline",
]
