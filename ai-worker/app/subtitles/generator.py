"""Subtitle generator orchestrator transforming transcript segments into exportable formats."""
from typing import Any, Dict, List, Optional, Union
from app.subtitles.formatters import (
    format_json,
    format_srt,
    format_txt,
    format_vtt,
    validate_and_normalize_entries,
)
from app.subtitles.models import (
    SubtitleEntry,
    SubtitleFormat,
    SubtitleGenerationRequest,
    SubtitleResult,
)


class SubtitleGenerator:
    """Orchestrates generation of multi-format subtitle artifacts."""

    def __init__(self) -> None:
        pass

    def convert_to_entries(
        self,
        segments: List[Union[SubtitleEntry, Dict[str, Any], Any]],
        default_language: str = "en",
        speaker_map: Optional[Dict[str, str]] = None
    ) -> List[SubtitleEntry]:
        """Normalize heterogeneous segment representations into SubtitleEntry objects."""
        entries: List[SubtitleEntry] = []

        for idx, seg in enumerate(segments, start=1):
            if isinstance(seg, SubtitleEntry):
                entries.append(seg)
                continue

            if isinstance(seg, dict):
                start = float(seg.get("start_time", seg.get("startTime", 0.0)))
                end = float(seg.get("end_time", seg.get("endTime", start + 1.0)))
                text = seg.get("text", seg.get("translated_text", seg.get("translatedText", "")))
                speaker = seg.get("speaker", seg.get("speaker_label", seg.get("speakerLabel", None)))
                speaker_name = seg.get("speaker_name", seg.get("speakerName", None))
                conf = float(seg.get("confidence", 1.0))
                lang = seg.get("language", seg.get("target_language", seg.get("targetLanguage", default_language)))

                if not speaker_name and speaker and speaker_map and speaker in speaker_map:
                    speaker_name = speaker_map[speaker]

                entries.append(SubtitleEntry(
                    index=idx,
                    start_time=start,
                    end_time=end,
                    text=str(text or "").strip(),
                    speaker=speaker,
                    speaker_name=speaker_name,
                    confidence=conf,
                    language=lang
                ))
                continue

            # Check for Pydantic objects or objects with attributes
            # TranslatedSegment check (translated_text, speaker_label, etc.)
            if hasattr(seg, "translated_text"):
                start = float(getattr(seg, "start_time", 0.0))
                end = float(getattr(seg, "end_time", start + 1.0))
                text = getattr(seg, "translated_text", "")
                speaker = getattr(seg, "speaker_label", None)
                conf = float(getattr(seg, "confidence", 1.0))
                lang = getattr(seg, "target_language", default_language)
                speaker_name = speaker_map.get(speaker) if (speaker_map and speaker) else None

                entries.append(SubtitleEntry(
                    index=idx,
                    start_time=start,
                    end_time=end,
                    text=str(text or "").strip(),
                    speaker=speaker,
                    speaker_name=speaker_name,
                    confidence=conf,
                    language=lang
                ))
                continue

            # Standard TranscriptionSegment check (text, start_time, end_time, speaker)
            if hasattr(seg, "text") and hasattr(seg, "start_time"):
                start = float(getattr(seg, "start_time", 0.0))
                end = float(getattr(seg, "end_time", start + 1.0))
                text = getattr(seg, "text", "")
                speaker = getattr(seg, "speaker", None)
                conf = float(getattr(seg, "confidence", 1.0))
                lang = getattr(seg, "language", default_language)
                speaker_name = speaker_map.get(speaker) if (speaker_map and speaker) else None

                entries.append(SubtitleEntry(
                    index=idx,
                    start_time=start,
                    end_time=end,
                    text=str(text or "").strip(),
                    speaker=speaker,
                    speaker_name=speaker_name,
                    confidence=conf,
                    language=lang
                ))
                continue

        return validate_and_normalize_entries(entries)

    def generate(self, request: SubtitleGenerationRequest) -> SubtitleResult:
        """Generate formatted subtitles from the provided request."""
        entries = self.convert_to_entries(
            request.segments,
            default_language=request.language,
            speaker_map=request.speaker_map
        )

        fmt = request.format
        if isinstance(fmt, str):
            fmt = SubtitleFormat.from_str(fmt)

        duration = entries[-1].end_time if entries else 0.0

        if fmt == SubtitleFormat.SRT:
            content = format_srt(
                entries,
                include_speakers=request.include_speakers,
                speaker_map=request.speaker_map,
                max_chars_per_line=request.max_chars_per_line,
                max_lines=request.max_lines
            )
        elif fmt == SubtitleFormat.VTT:
            content = format_vtt(
                entries,
                include_speakers=request.include_speakers,
                speaker_map=request.speaker_map,
                max_chars_per_line=request.max_chars_per_line,
                max_lines=request.max_lines
            )
        elif fmt == SubtitleFormat.TXT:
            content = format_txt(
                entries,
                include_speakers=request.include_speakers,
                include_timestamps=True,
                speaker_map=request.speaker_map
            )
        elif fmt == SubtitleFormat.JSON:
            content = format_json(
                entries,
                language=request.language,
                include_speakers=request.include_speakers,
                speaker_map=request.speaker_map
            )
        else:
            raise ValueError(f"Unsupported format: {fmt}")

        return SubtitleResult(
            content=content,
            format=fmt,
            language=request.language,
            total_cues=len(entries),
            duration_seconds=duration,
            includes_speakers=request.include_speakers,
            file_extension=fmt.file_extension,
            mime_type=fmt.mime_type
        )
