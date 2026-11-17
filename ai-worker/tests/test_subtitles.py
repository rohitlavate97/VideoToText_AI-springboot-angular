"""Tests for subtitle generation module (SRT, VTT, TXT, JSON, timestamps, speakers)."""
import json
import pytest
from app.providers.base_provider import TranscriptionSegment
from app.translation.models import TranslatedSegment
from app.subtitles.formatters import (
    format_json,
    format_srt,
    format_timestamp_srt,
    format_timestamp_txt,
    format_timestamp_vtt,
    format_txt,
    format_vtt,
    validate_and_normalize_entries,
    wrap_text,
)
from app.subtitles.generator import SubtitleGenerator
from app.subtitles.models import (
    SubtitleEntry,
    SubtitleFormat,
    SubtitleGenerationRequest,
    SubtitleResult,
)


def test_timestamp_formatting():
    """Verify SRT, WebVTT, and TXT timestamp representations."""
    # 0 seconds
    assert format_timestamp_srt(0.0) == "00:00:00,000"
    assert format_timestamp_vtt(0.0) == "00:00:00.000"
    assert format_timestamp_txt(0.0) == "[00:00:00]"

    # Fractional seconds
    assert format_timestamp_srt(1.234) == "00:00:01,234"
    assert format_timestamp_vtt(1.234) == "00:00:01.234"

    # Over 1 minute
    assert format_timestamp_srt(65.5) == "00:01:05,500"
    assert format_timestamp_vtt(65.5) == "00:01:05.500"
    assert format_timestamp_txt(65.5) == "[00:01:05]"

    # Over 1 hour
    assert format_timestamp_srt(3661.05) == "01:01:01,050"
    assert format_timestamp_vtt(3661.05) == "01:01:01.050"
    assert format_timestamp_txt(3661.05) == "[01:01:01]"

    # Negative clamped to 0
    assert format_timestamp_srt(-5.0) == "00:00:00,000"


def test_validate_and_normalize_entries():
    """Verify chronological ordering, negative clamping, and inverted end time recovery."""
    raw = [
        SubtitleEntry(index=99, start_time=5.0, end_time=4.0, text="Inverted timing"),
        SubtitleEntry(index=2, start_time=1.0, end_time=3.0, text="First cue"),
        SubtitleEntry(index=5, start_time=-2.0, end_time=0.5, text="Negative start"),
        SubtitleEntry(index=8, start_time=10.0, end_time=12.0, text="   "),  # Empty text
    ]

    normalized = validate_and_normalize_entries(raw)
    assert len(normalized) == 3

    # Check chronological order
    assert normalized[0].start_time == 0.0
    assert normalized[0].index == 1
    assert normalized[0].text == "Negative start"

    assert normalized[1].start_time == 1.0
    assert normalized[1].index == 2
    assert normalized[1].text == "First cue"

    assert normalized[2].start_time == 5.0
    assert normalized[2].end_time >= 5.1  # Inverted fixed
    assert normalized[2].index == 3
    assert normalized[2].text == "Inverted timing"


def test_wrap_text():
    """Verify text wrapping on word boundaries within limits."""
    short = "Hello world"
    assert wrap_text(short, max_chars_per_line=42) == "Hello world"

    long_line = "This is a long sentence that should definitely be wrapped onto a second line for subtitles."
    wrapped = wrap_text(long_line, max_chars_per_line=40, max_lines=2)
    lines = wrapped.split("\n")
    assert len(lines) == 2
    assert len(lines[0]) <= 40


def test_format_srt():
    """Verify standard SubRip formatting."""
    entries = [
        SubtitleEntry(index=1, start_time=1.0, end_time=4.5, text="Welcome to the demo.", speaker="SPEAKER_00"),
        SubtitleEntry(index=2, start_time=5.0, end_time=8.2, text="Thank you for joining.", speaker="SPEAKER_01"),
    ]

    # Without speakers
    srt = format_srt(entries, include_speakers=False)
    assert "1\n00:00:01,000 --> 00:00:04,500\nWelcome to the demo." in srt
    assert "2\n00:00:05,000 --> 00:00:08,200\nThank you for joining." in srt
    assert "[SPEAKER_00]" not in srt

    # With speaker mapping
    speaker_map = {"SPEAKER_00": "Alice", "SPEAKER_01": "Bob"}
    srt_speakers = format_srt(entries, include_speakers=True, speaker_map=speaker_map)
    assert "[Alice]: Welcome to the demo." in srt_speakers
    assert "[Bob]: Thank you for joining." in srt_speakers


def test_format_vtt():
    """Verify WebVTT formatting with voice tags."""
    entries = [
        SubtitleEntry(index=1, start_time=1.0, end_time=4.5, text="Welcome to the demo.", speaker="Alice"),
    ]

    # Without speakers
    vtt = format_vtt(entries, include_speakers=False)
    assert vtt.startswith("WEBVTT\n")
    assert "00:00:01.000 --> 00:00:04.500" in vtt
    assert "Welcome to the demo." in vtt

    # With speakers
    vtt_speakers = format_vtt(entries, include_speakers=True)
    assert "<v Alice>Welcome to the demo." in vtt_speakers


def test_format_txt():
    """Verify plain text format with and without timestamps."""
    entries = [
        SubtitleEntry(index=1, start_time=1.0, end_time=4.5, text="Hello world.", speaker="Alice"),
        SubtitleEntry(index=2, start_time=5.0, end_time=8.0, text="How are you?", speaker="Bob"),
    ]

    txt = format_txt(entries, include_speakers=True, include_timestamps=True)
    assert "[00:00:01] [Alice]: Hello world." in txt
    assert "[00:00:05] [Bob]: How are you?" in txt

    txt_no_ts = format_txt(entries, include_speakers=True, include_timestamps=False)
    assert "[Alice]: Hello world." in txt_no_ts
    assert "[00:00:01]" not in txt_no_ts


def test_format_json():
    """Verify structured JSON format output."""
    entries = [
        SubtitleEntry(index=1, start_time=1.0, end_time=4.5, text="Hello.", speaker="Alice", confidence=0.98),
    ]

    json_str = format_json(entries, language="en", include_speakers=True)
    parsed = json.loads(json_str)

    assert parsed["format"] == "json"
    assert parsed["language"] == "en"
    assert parsed["totalCues"] == 1
    assert parsed["duration"] == 4.5
    assert len(parsed["cues"]) == 1
    assert parsed["cues"][0]["id"] == 1
    assert parsed["cues"][0]["startTime"] == 1.0
    assert parsed["cues"][0]["endTime"] == 4.5
    assert parsed["cues"][0]["speaker"] == "Alice"
    assert parsed["cues"][0]["text"] == "Hello."


def test_generator_heterogeneous_segments():
    """Verify SubtitleGenerator correctly parses TranscriptionSegment, TranslatedSegment, and dicts."""
    generator = SubtitleGenerator()

    # 1. TranscriptionSegment
    seg1 = TranscriptionSegment(
        id=1,
        start_time=0.5,
        end_time=3.2,
        text="This is an ASR segment.",
        confidence=0.95,
        speaker="SPEAKER_00"
    )

    # 2. TranslatedSegment
    seg2 = TranslatedSegment(
        segment_index=1,
        start_time=3.5,
        end_time=6.0,
        source_text="Hello",
        translated_text="नमस्ते दुनिया",
        speaker_label="SPEAKER_01",
        target_language="hi",
        confidence=0.9
    )

    # 3. Plain dict
    seg3 = {
        "startTime": 6.5,
        "endTime": 9.0,
        "text": "Dict segment text",
        "speaker": "SPEAKER_00"
    }

    req = SubtitleGenerationRequest(
        segments=[seg1, seg2, seg3],
        format=SubtitleFormat.SRT,
        language="hi",
        include_speakers=True,
        speaker_map={"SPEAKER_00": "Host", "SPEAKER_01": "Guest"}
    )

    result = generator.generate(req)
    assert isinstance(result, SubtitleResult)
    assert result.total_cues == 3
    assert result.language == "hi"
    assert result.duration_seconds == 9.0
    assert "[Host]: This is an ASR segment." in result.content
    assert "[Guest]: नमस्ते दुनिया" in result.content
    assert "[Host]: Dict segment text" in result.content


def test_empty_subtitles():
    """Verify handling of empty or whitespace cues."""
    generator = SubtitleGenerator()
    req = SubtitleGenerationRequest(
        segments=[],
        format=SubtitleFormat.VTT
    )
    result = generator.generate(req)
    assert result.total_cues == 0
    assert result.content.strip() == "WEBVTT"


def test_format_enum_and_mimes():
    """Verify format string conversions, mime types, and file extensions."""
    assert SubtitleFormat.from_str("SRT") == SubtitleFormat.SRT
    assert SubtitleFormat.from_str("vtt") == SubtitleFormat.VTT
    assert SubtitleFormat.from_str("Txt") == SubtitleFormat.TXT
    assert SubtitleFormat.from_str("JSON") == SubtitleFormat.JSON

    assert SubtitleFormat.SRT.file_extension == ".srt"
    assert SubtitleFormat.VTT.file_extension == ".vtt"
    assert SubtitleFormat.TXT.file_extension == ".txt"
    assert SubtitleFormat.JSON.file_extension == ".json"

    assert SubtitleFormat.SRT.mime_type == "application/x-subrip"
    assert SubtitleFormat.VTT.mime_type == "text/vtt"
    assert SubtitleFormat.TXT.mime_type == "text/plain"
    assert SubtitleFormat.JSON.mime_type == "application/json"
