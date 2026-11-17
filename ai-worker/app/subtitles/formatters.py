"""Formatters for subtitle files (SRT, WebVTT, TXT, JSON) with timestamp validation."""
import json
import math
from typing import Dict, List, Optional
from app.subtitles.models import SubtitleEntry, SubtitleFormat


def format_timestamp_parts(seconds: float) -> tuple[int, int, int, int]:
    """Break seconds float into (hours, minutes, seconds, milliseconds) with rollover handling."""
    if seconds < 0:
        seconds = 0.0

    total_millis = int(round(seconds * 1000))
    hours = total_millis // 3_600_000
    remainder = total_millis % 3_600_000
    minutes = remainder // 60_000
    remainder = remainder % 60_000
    secs = remainder // 1_000
    millis = remainder % 1_000

    return hours, minutes, secs, millis


def format_timestamp_srt(seconds: float) -> str:
    """Format seconds into SRT timestamp string: HH:MM:SS,mmm."""
    h, m, s, ms = format_timestamp_parts(seconds)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def format_timestamp_vtt(seconds: float) -> str:
    """Format seconds into WebVTT timestamp string: HH:MM:SS.mmm."""
    h, m, s, ms = format_timestamp_parts(seconds)
    return f"{h:02d}:{m:02d}:{s:02d}.{ms:03d}"


def format_timestamp_txt(seconds: float) -> str:
    """Format seconds into short display timestamp string: [HH:MM:SS]."""
    h, m, s, _ = format_timestamp_parts(seconds)
    return f"[{h:02d}:{m:02d}:{s:02d}]"


def resolve_speaker_display(entry: SubtitleEntry, speaker_map: Optional[Dict[str, str]] = None) -> Optional[str]:
    """Resolve human-readable display name for speaker."""
    if not entry.speaker and not entry.speaker_name:
        return None
    raw = entry.speaker or entry.speaker_name
    if speaker_map and raw in speaker_map:
        return speaker_map[raw]
    return entry.speaker_name or raw


def wrap_text(text: str, max_chars_per_line: int = 42, max_lines: int = 2) -> str:
    """
    Wrap subtitle text gracefully across max_lines with max_chars_per_line boundary.
    Splits at word boundaries.
    """
    text = " ".join(text.strip().split())
    if len(text) <= max_chars_per_line:
        return text

    words = text.split(" ")
    lines: List[str] = []
    current_line: List[str] = []
    current_length = 0

    for word in words:
        word_len = len(word)
        if current_line and (current_length + 1 + word_len) > max_chars_per_line:
            lines.append(" ".join(current_line))
            current_line = [word]
            current_length = word_len
        else:
            current_line.append(word)
            current_length += (word_len + 1) if current_length > 0 else word_len

    if current_line:
        lines.append(" ".join(current_line))

    # If exceeding max_lines, fold remaining lines into the last line
    if len(lines) > max_lines:
        first_lines = lines[:max_lines - 1]
        last_line = " ".join(lines[max_lines - 1:])
        return "\n".join(first_lines + [last_line])

    return "\n".join(lines)


def validate_and_normalize_entries(entries: List[SubtitleEntry]) -> List[SubtitleEntry]:
    """
    Validates chronological order, positive timestamps, and non-inverted start/end times.
    Re-numbers cues sequentially starting at 1.
    """
    if not entries:
        return []

    # Filter out empty or whitespace-only cues
    valid = [e for e in entries if e.text and e.text.strip()]
    if not valid:
        return []

    # Sort strictly by start_time
    sorted_entries = sorted(valid, key=lambda e: (e.start_time, e.end_time))

    normalized: List[SubtitleEntry] = []
    for idx, e in enumerate(sorted_entries, start=1):
        start = max(0.0, float(e.start_time))
        end = float(e.end_time)
        if end <= start:
            # Enforce minimum positive span (100ms)
            end = start + 0.1

        normalized.append(SubtitleEntry(
            index=idx,
            start_time=round(start, 3),
            end_time=round(end, 3),
            text=e.text.strip(),
            speaker=e.speaker,
            speaker_name=e.speaker_name,
            confidence=e.confidence,
            language=e.language
        ))

    return normalized


def format_srt(
    entries: List[SubtitleEntry],
    include_speakers: bool = False,
    speaker_map: Optional[Dict[str, str]] = None,
    max_chars_per_line: int = 42,
    max_lines: int = 2
) -> str:
    """
    Format subtitle entries into SubRip (.srt) format:
    1
    00:00:01,000 --> 00:00:04,500
    [Speaker]: Text
    """
    normalized = validate_and_normalize_entries(entries)
    if not normalized:
        return ""

    blocks: List[str] = []
    for entry in normalized:
        start_str = format_timestamp_srt(entry.start_time)
        end_str = format_timestamp_srt(entry.end_time)

        speaker_display = resolve_speaker_display(entry, speaker_map) if include_speakers else None
        text = entry.text
        if speaker_display:
            text = f"[{speaker_display}]: {text}"

        wrapped_text = wrap_text(text, max_chars_per_line, max_lines)
        block = f"{entry.index}\n{start_str} --> {end_str}\n{wrapped_text}"
        blocks.append(block)

    return "\n\n".join(blocks) + "\n"


def format_vtt(
    entries: List[SubtitleEntry],
    include_speakers: bool = False,
    speaker_map: Optional[Dict[str, str]] = None,
    max_chars_per_line: int = 42,
    max_lines: int = 2
) -> str:
    """
    Format subtitle entries into WebVTT (.vtt) format:
    WEBVTT

    1
    00:00:01.000 --> 00:00:04.500
    <v Speaker>Text
    """
    normalized = validate_and_normalize_entries(entries)
    header = "WEBVTT\n"
    if not normalized:
        return header

    blocks: List[str] = []
    for entry in normalized:
        start_str = format_timestamp_vtt(entry.start_time)
        end_str = format_timestamp_vtt(entry.end_time)

        speaker_display = resolve_speaker_display(entry, speaker_map) if include_speakers else None
        text = entry.text
        if speaker_display:
            text = f"<v {speaker_display}>{text}"

        wrapped_text = wrap_text(text, max_chars_per_line, max_lines)
        block = f"{entry.index}\n{start_str} --> {end_str}\n{wrapped_text}"
        blocks.append(block)

    return header + "\n" + "\n\n".join(blocks) + "\n"


def format_txt(
    entries: List[SubtitleEntry],
    include_speakers: bool = False,
    include_timestamps: bool = True,
    speaker_map: Optional[Dict[str, str]] = None
) -> str:
    """
    Format subtitle entries into clean plain text (.txt):
    [00:00:01] [Speaker]: Text
    """
    normalized = validate_and_normalize_entries(entries)
    if not normalized:
        return ""

    lines: List[str] = []
    for entry in normalized:
        speaker_display = resolve_speaker_display(entry, speaker_map) if include_speakers else None

        parts = []
        if include_timestamps:
            parts.append(format_timestamp_txt(entry.start_time))
        if speaker_display:
            parts.append(f"[{speaker_display}]:")
        parts.append(entry.text)

        lines.append(" ".join(parts))

    return "\n".join(lines) + "\n"


def format_json(
    entries: List[SubtitleEntry],
    language: str = "en",
    include_speakers: bool = False,
    speaker_map: Optional[Dict[str, str]] = None
) -> str:
    """
    Format subtitle entries into structured JSON format.
    """
    normalized = validate_and_normalize_entries(entries)
    duration = normalized[-1].end_time if normalized else 0.0

    cues = []
    for entry in normalized:
        cue = {
            "id": entry.index,
            "startTime": entry.start_time,
            "endTime": entry.end_time,
            "duration": round(entry.end_time - entry.start_time, 3),
            "text": entry.text,
            "confidence": entry.confidence,
            "language": entry.language or language
        }
        if include_speakers:
            speaker_display = resolve_speaker_display(entry, speaker_map)
            cue["speaker"] = speaker_display
        cues.append(cue)

    payload = {
        "format": "json",
        "language": language,
        "totalCues": len(cues),
        "duration": duration,
        "cues": cues
    }
    return json.dumps(payload, indent=2, ensure_ascii=False)
