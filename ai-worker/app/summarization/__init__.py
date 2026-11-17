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
from app.summarization.engine import (
    LocalHeuristicSummaryEngine,
    HostedLLMSummaryEngine,
    SummaryEngineFactory,
)

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
    "LocalHeuristicSummaryEngine",
    "HostedLLMSummaryEngine",
    "SummaryEngineFactory",
]
