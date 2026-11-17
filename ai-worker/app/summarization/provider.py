from abc import ABC, abstractmethod
from typing import List, Optional
import logging

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

logger = logging.getLogger(__name__)


class BaseSummaryProvider(ABC):
    """
    Abstract Base Class for video summarization and intelligent insights engines.
    Adheres to privacy constraints and pluggable LLM/local execution models.
    """

    @property
    @abstractmethod
    def provider_name(self) -> str:
        """Name of the provider implementation (e.g. 'local', 'mock', 'openai')."""
        pass

    @property
    @abstractmethod
    def model_name(self) -> str:
        """Name of the underlying LLM or algorithm used."""
        pass

    @abstractmethod
    def generate_summary(
        self,
        text: str,
        segments: List[TranscriptSegmentInput],
        length: SummaryLength,
        format_type: SummaryFormat
    ) -> str:
        """Synthesizes video summary text according to length and format options."""
        pass

    @abstractmethod
    def extract_key_points(
        self,
        text: str,
        segments: List[TranscriptSegmentInput],
        max_points: int = 5
    ) -> List[str]:
        """Extracts top key takeaways and salient conclusions."""
        pass

    @abstractmethod
    def extract_action_items(
        self,
        text: str,
        segments: List[TranscriptSegmentInput]
    ) -> List[ActionItem]:
        """Detects commitments, assignments, next steps, and follow-up tasks with timestamps."""
        pass

    @abstractmethod
    def extract_topics(
        self,
        text: str,
        segments: List[TranscriptSegmentInput]
    ) -> List[Topic]:
        """Identifies key discussion topics with relevance scores and timestamps."""
        pass

    @abstractmethod
    def generate_chapters(
        self,
        segments: List[TranscriptSegmentInput]
    ) -> List[Chapter]:
        """Partitions chronological transcript into coherent video chapters."""
        pass

    def generate_insights(self, request: InsightRequest) -> InsightResult:
        """
        Orchestrates full insight generation from the transcript request.
        """
        text = request.transcript_text
        if not text and request.segments:
            text = " ".join(s.text.strip() for s in request.segments if s.text)
        text = text or ""

        summary_text = self.generate_summary(
            text=text,
            segments=request.segments,
            length=request.summary_length,
            format_type=request.summary_format
        )

        key_points = (
            self.extract_key_points(text, request.segments)
            if request.extract_key_points
            else []
        )

        action_items = (
            self.extract_action_items(text, request.segments)
            if request.extract_action_items
            else []
        )

        topics = (
            self.extract_topics(text, request.segments)
            if request.extract_topics
            else []
        )

        chapters = (
            self.generate_chapters(request.segments)
            if request.generate_chapters and request.segments
            else []
        )

        return InsightResult(
            summary_text=summary_text,
            summary_length=request.summary_length.value,
            summary_format=request.summary_format.value,
            key_points=key_points,
            action_items=action_items,
            topics=topics,
            chapters=chapters,
            llm_provider=self.provider_name,
            llm_model=self.model_name
        )

    @staticmethod
    def enforce_privacy(target_provider: str, privacy_mode: PrivacyMode) -> None:
        """
        Validates that external/cloud providers are prohibited when LOCAL_ONLY privacy is active.
        """
        cloud_providers = {"openai", "cloud", "hosted", "anthropic", "azure"}
        if privacy_mode == PrivacyMode.LOCAL_ONLY and target_provider.lower().strip() in cloud_providers:
            raise PermissionError(
                f"Privacy policy violation: Provider '{target_provider}' is blocked under LOCAL_ONLY privacy mode."
            )


class MockSummaryProvider(BaseSummaryProvider):
    """
    Deterministic mock summary provider for testing, rapid benchmarking, and CI.
    Generates structured, reproducible outputs without external model calls.
    """

    def __init__(self, model_name: str = "mock-summary-v1"):
        self._model_name = model_name

    @property
    def provider_name(self) -> str:
        return "mock"

    @property
    def model_name(self) -> str:
        return self._model_name

    def generate_summary(
        self,
        text: str,
        segments: List[TranscriptSegmentInput],
        length: SummaryLength,
        format_type: SummaryFormat
    ) -> str:
        word_count = len(text.split()) if text else 0
        base = f"[MOCK SUMMARY - {length.value.upper()}] Overview of transcript with {word_count} words."
        
        if length == SummaryLength.SHORT:
            content = f"{base} Key points were discussed and agreements were reached."
        elif length == SummaryLength.DETAILED:
            content = (
                f"{base}\n\n"
                f"Section 1: The discussion commenced with introductory remarks and agenda alignment.\n\n"
                f"Section 2: Detailed technical topics, architecture decisions, and timelines were evaluated.\n\n"
                f"Section 3: Key outcomes, action items, and next milestones were finalized for the team."
            )
        else:  # MEDIUM
            content = (
                f"{base} The session covered project milestones, technical deliverables, and strategic decisions. "
                f"All participants aligned on the upcoming schedule."
            )

        if format_type == SummaryFormat.BULLETS:
            sentences = [s.strip() for s in content.split(". ") if s.strip()]
            return "\n".join(f"• {s.rstrip('.')}" for s in sentences)
        elif format_type == SummaryFormat.EXECUTIVE:
            return f"Executive Brief:\n{content}\n\nOutcome: Next steps approved."
        return content

    def extract_key_points(
        self,
        text: str,
        segments: List[TranscriptSegmentInput],
        max_points: int = 5
    ) -> List[str]:
        if not text and not segments:
            return ["No content provided for key point extraction."]
        points = [
            "Project architecture and delivery roadmap reviewed.",
            "Key engineering milestones established for upcoming sprint.",
            "Cross-functional team alignment on quality and performance goals.",
            "Timelines and resource allocations approved.",
            "Follow-up reviews scheduled for pending deliverables."
        ]
        return points[:max_points]

    def extract_action_items(
        self,
        text: str,
        segments: List[TranscriptSegmentInput]
    ) -> List[ActionItem]:
        items: List[ActionItem] = []
        if segments:
            for seg in segments:
                t = seg.text.lower()
                if any(w in t for w in ["will", "need to", "action", "follow up", "task", "todo", "make sure"]):
                    items.append(
                        ActionItem(
                            task=seg.text.strip(),
                            assignee=seg.speaker_label or "Participant",
                            timestamp=seg.start_time,
                            completed=False
                        )
                    )
        if not items:
            items.append(
                ActionItem(
                    task="Review transcription accuracy and finalize action points",
                    assignee=segments[0].speaker_label if segments else "Lead",
                    timestamp=segments[0].start_time if segments else 0.0,
                    completed=False
                )
            )
        return items

    def extract_topics(
        self,
        text: str,
        segments: List[TranscriptSegmentInput]
    ) -> List[Topic]:
        return [
            Topic(name="Project Architecture", relevance=0.95, start_time=segments[0].start_time if segments else 0.0),
            Topic(name="Implementation Details", relevance=0.85, start_time=segments[len(segments)//2].start_time if segments else 10.0),
            Topic(name="Next Milestones", relevance=0.75, start_time=segments[-1].start_time if segments else 20.0),
        ]

    def generate_chapters(
        self,
        segments: List[TranscriptSegmentInput]
    ) -> List[Chapter]:
        if not segments:
            return []
        
        n = len(segments)
        if n < 3:
            return [
                Chapter(
                    title="Overview & Discussion",
                    start_time=segments[0].start_time,
                    end_time=segments[-1].end_time,
                    summary="Full discussion covering key topics."
                )
            ]

        p1 = n // 3
        p2 = (2 * n) // 3

        return [
            Chapter(
                title="Introduction & Context",
                start_time=segments[0].start_time,
                end_time=segments[p1 - 1].end_time,
                summary="Initial setup, agenda review, and introductory remarks."
            ),
            Chapter(
                title="Core Discussion & Analysis",
                start_time=segments[p1].start_time,
                end_time=segments[p2 - 1].end_time,
                summary="In-depth exploration of technical implementation and strategies."
            ),
            Chapter(
                title="Decisions & Next Steps",
                start_time=segments[p2].start_time,
                end_time=segments[-1].end_time,
                summary="Consolidation of outcomes, assigned deliverables, and conclusion."
            ),
        ]
