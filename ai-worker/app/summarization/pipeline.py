from typing import List, Optional, Union
from uuid import UUID
import logging

from app.providers.base_provider import TranscriptionSegment
from app.summarization.models import (
    SummaryLength,
    SummaryFormat,
    PrivacyMode,
    TranscriptSegmentInput,
    InsightRequest,
    InsightResult,
)
from app.summarization.engine import SummaryEngineFactory
from app.summarization.provider import BaseSummaryProvider

logger = logging.getLogger(__name__)


class SummaryPipeline:
    """
    Pipeline orchestrating end-to-end video summarization and intelligent insight extraction.
    Converts transcription segments into standardized inputs, invokes configured summary engines,
    and returns comprehensive structured results.
    """

    def __init__(
        self,
        engine: Optional[BaseSummaryProvider] = None,
        provider_name: Optional[str] = None,
        privacy_mode: Optional[PrivacyMode] = None,
        api_key: Optional[str] = None,
        base_url: Optional[str] = None,
        model_name: Optional[str] = None,
    ):
        if engine is not None:
            self.engine = engine
        else:
            self.engine = SummaryEngineFactory.create_engine(
                provider_name=provider_name,
                privacy_mode=privacy_mode,
                api_key=api_key,
                base_url=base_url,
                model_name=model_name
            )

    @staticmethod
    def normalize_segments(
        segments: List[Union[TranscriptionSegment, TranscriptSegmentInput, dict]]
    ) -> List[TranscriptSegmentInput]:
        """Converts heterogeneous segment representations into TranscriptSegmentInput objects."""
        normalized: List[TranscriptSegmentInput] = []
        for idx, s in enumerate(segments):
            if isinstance(s, TranscriptSegmentInput):
                normalized.append(s)
            elif isinstance(s, TranscriptionSegment):
                normalized.append(
                    TranscriptSegmentInput(
                        segment_index=s.id if s.id is not None else idx,
                        start_time=s.start_time,
                        end_time=s.end_time,
                        speaker_label=s.speaker or getattr(s, "speaker_label", None),
                        text=s.text
                    )
                )
            elif isinstance(s, dict):
                normalized.append(
                    TranscriptSegmentInput(
                        segment_index=s.get("segment_index", idx),
                        start_time=float(s.get("start_time", 0.0)),
                        end_time=float(s.get("end_time", 0.0)),
                        speaker_label=s.get("speaker_label"),
                        text=s.get("text", "")
                    )
                )
        return normalized

    def process(
        self,
        segments: List[Union[TranscriptionSegment, TranscriptSegmentInput, dict]],
        job_id: Optional[UUID] = None,
        transcript_text: Optional[str] = None,
        summary_length: SummaryLength = SummaryLength.MEDIUM,
        summary_format: SummaryFormat = SummaryFormat.PARAGRAPH,
        extract_key_points: bool = True,
        extract_action_items: bool = True,
        extract_topics: bool = True,
        generate_chapters: bool = True,
    ) -> InsightResult:
        """
        Executes the summarization and insight pipeline over the input segments.
        """
        norm_segments = self.normalize_segments(segments)
        logger.info(
            "Executing summary pipeline with provider=%s model=%s (length=%s, format=%s, segments=%d)",
            self.engine.provider_name,
            self.engine.model_name,
            summary_length.value,
            summary_format.value,
            len(norm_segments)
        )

        request = InsightRequest(
            job_id=job_id,
            transcript_text=transcript_text,
            segments=norm_segments,
            summary_length=summary_length,
            summary_format=summary_format,
            extract_key_points=extract_key_points,
            extract_action_items=extract_action_items,
            extract_topics=extract_topics,
            generate_chapters=generate_chapters
        )

        return self.engine.generate_insights(request)
