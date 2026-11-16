import logging
from typing import Callable, Dict, List, Optional
from uuid import UUID
from app.providers.base_provider import TranscriptionSegment
from app.translation.engine import TranslationEngineFactory
from app.translation.models import (
    SupportedLanguage,
    TranslatedSegment,
    TranslationResult,
)
from app.translation.provider import BaseTranslationProvider
from app.translation.quality import TranslationQualityChecker

logger = logging.getLogger(__name__)


class TranslationPipeline:
    """
    Multilingual translation pipeline orchestrating batch translation across target languages
    while strictly preserving segment timestamps, speaker attribution labels, and ordering.
    """

    def __init__(
        self,
        provider: Optional[BaseTranslationProvider] = None,
        batch_size: int = 16
    ):
        self.provider = provider or TranslationEngineFactory.create_engine()
        self.batch_size = max(1, batch_size)

    def translate_segments(
        self,
        job_id: UUID,
        segments: List[TranscriptionSegment],
        source_lang: str,
        target_lang: str
    ) -> TranslationResult:
        """
        Translates a list of transcript segments into a target language,
        strictly preserving start_time, end_time, and speaker_label.
        """
        if not segments:
            return TranslationResult(
                job_id=job_id,
                source_language=source_lang,
                target_language=target_lang,
                translation_engine=self.provider.__class__.__name__,
                segments=[],
                full_text="",
                quality_score=1.0
            )

        logger.info(
            "Translating %d segments for job %s from %s to %s",
            len(segments),
            job_id,
            source_lang,
            target_lang
        )

        translated_segments: List[TranslatedSegment] = []
        quality_scores: List[float] = []

        # Process in batches for performance
        for i in range(0, len(segments), self.batch_size):
            batch = segments[i:i + self.batch_size]
            source_texts = [seg.text for seg in batch]

            try:
                translated_texts = self.provider.translate_batch(source_texts, source_lang, target_lang)
            except Exception as e:
                logger.warning("Batch translation failed (%s), attempting per-segment translation with fallback: %s", e, e)
                translated_texts = []
                for t in source_texts:
                    try:
                        translated_texts.append(self.provider.translate(t, source_lang, target_lang))
                    except Exception:
                        translated_texts.append(t)  # Fallback to source text on critical failure

            for seg, trans_text in zip(batch, translated_texts):
                report = TranslationQualityChecker.evaluate(seg.text, trans_text, target_lang)
                quality_scores.append(report.estimated_quality_score)

                speaker = getattr(seg, "speaker", None) or getattr(seg, "speaker_label", None)
                seg_idx = getattr(seg, "segment_index", getattr(seg, "id", 0))
                translated_segments.append(
                    TranslatedSegment(
                        segment_index=seg_idx,
                        start_time=seg.start_time,
                        end_time=seg.end_time,
                        speaker_label=speaker,
                        source_text=seg.text,
                        translated_text=trans_text,
                        target_language=target_lang,
                        confidence=report.estimated_quality_score
                    )
                )

        full_text = " ".join(ts.translated_text for ts in translated_segments if ts.translated_text)
        avg_quality = round(sum(quality_scores) / max(1, len(quality_scores)), 2)

        return TranslationResult(
            job_id=job_id,
            source_language=source_lang,
            target_language=target_lang,
            translation_engine=self.provider.__class__.__name__,
            segments=translated_segments,
            full_text=full_text,
            quality_score=avg_quality
        )

    def translate_all(
        self,
        job_id: UUID,
        segments: List[TranscriptionSegment],
        source_lang: str,
        target_languages: List[str],
        on_language_completed: Optional[Callable[[int, int, str], None]] = None
    ) -> Dict[str, TranslationResult]:
        """
        Translates segments into all requested target languages sequentially.
        """
        results: Dict[str, TranslationResult] = {}
        total = len(target_languages)

        for idx, lang in enumerate(target_languages, 1):
            clean_lang = lang.lower().strip()
            result = self.translate_segments(job_id, segments, source_lang, clean_lang)
            results[clean_lang] = result

            if on_language_completed:
                on_language_completed(idx, total, clean_lang)

        return results
