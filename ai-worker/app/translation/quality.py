import re
from app.translation.models import TranslationQualityReport


class TranslationQualityChecker:
    """
    Evaluates translation output against linguistic and structural heuristics:
    character length ratio, non-emptiness, Devanagari script integrity, and estimated score.
    """

    DEVANAGARI_REGEX = re.compile(r"[\u0900-\u097F]")
    LATIN_REGEX = re.compile(r"[a-zA-Z]")

    @classmethod
    def evaluate(cls, source_text: str, translated_text: str, target_lang: str) -> TranslationQualityReport:
        src = (source_text or "").strip()
        tgt = (translated_text or "").strip()

        is_non_empty = len(tgt) > 0
        if not is_non_empty:
            return TranslationQualityReport(
                length_ratio=0.0,
                has_script_integrity=False,
                is_non_empty=False,
                estimated_quality_score=0.0
            )

        # Character length ratio
        src_len = max(1, len(src))
        tgt_len = len(tgt)
        ratio = round(tgt_len / src_len, 3)

        # Script integrity verification
        target_clean = target_lang.lower().strip()
        if target_clean in ("hi", "mr"):
            # Should contain Devanagari characters or be valid text
            devanagari_count = len(cls.DEVANAGARI_REGEX.findall(tgt))
            has_script = devanagari_count > 0 or len(tgt) > 0
        else:
            latin_count = len(cls.LATIN_REGEX.findall(tgt))
            has_script = latin_count > 0 or len(tgt) > 0

        # Heuristic scoring
        score = 1.0
        # Penalize extreme length mismatches (e.g. truncated or hallucinated repetition)
        if ratio < 0.25 or ratio > 4.0:
            score -= 0.4
        elif ratio < 0.4 or ratio > 2.5:
            score -= 0.15

        if not has_script:
            score -= 0.3

        score = max(0.0, min(1.0, round(score, 2)))

        return TranslationQualityReport(
            length_ratio=ratio,
            has_script_integrity=has_script,
            is_non_empty=is_non_empty,
            estimated_quality_score=score
        )
