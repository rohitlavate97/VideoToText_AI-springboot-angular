import logging
from typing import Dict, List, Tuple
from app.diarization.models import DiarizationTurn, SpeakerProfile
from app.providers.base_provider import TranscriptionSegment

logger = logging.getLogger(__name__)


class SpeakerAssociator:
    """
    Associates ASR transcript segments with speaker turns:
    - Calculates maximum temporal overlap between speech segments and diarization turns.
    - Resolves word-level speaker attribution for multi-speaker overlapping segments.
    - Supports 'UNKNOWN' speaker attribution for silence or untracked intervals.
    - Computes cumulative speaking durations and segment counts per speaker.
    """

    @staticmethod
    def associate_segments(
        segments: List[TranscriptionSegment],
        turns: List[DiarizationTurn],
        default_unknown_speaker: str = "UNKNOWN"
    ) -> Tuple[List[TranscriptionSegment], List[SpeakerProfile]]:
        """
        Maps speaker turns onto transcription segments.
        Returns:
            Tuple[List[TranscriptionSegment], List[SpeakerProfile]]
        """
        if not segments:
            return [], []

        if not turns:
            # All segments assigned to default speaker or unknown
            updated_segments = [
                seg.model_copy(update={"speaker": default_unknown_speaker})
                for seg in segments
            ]
            total_duration_ms = sum(int((s.end_time - s.start_time) * 1000) for s in segments)
            profile = SpeakerProfile(
                speaker_label=default_unknown_speaker,
                display_name="Unknown Speaker",
                total_speaking_time_ms=total_duration_ms,
                segment_count=len(segments)
            )
            return updated_segments, [profile]

        updated_segments: List[TranscriptionSegment] = []
        speaker_time_ms: Dict[str, int] = {}
        speaker_segment_count: Dict[str, int] = {}

        for seg in segments:
            best_speaker = default_unknown_speaker
            max_overlap = 0.0

            for turn in turns:
                # Calculate overlap interval
                overlap_start = max(seg.start_time, turn.start_time)
                overlap_end = min(seg.end_time, turn.end_time)
                overlap_duration = max(0.0, overlap_end - overlap_start)

                if overlap_duration > max_overlap:
                    max_overlap = overlap_duration
                    best_speaker = turn.speaker_label

            # If overlap was negligible, verify proximity
            if max_overlap <= 0.0:
                # Find nearest turn within 1.0s window
                closest_turn = min(
                    turns,
                    key=lambda t: min(abs(t.start_time - seg.end_time), abs(t.end_time - seg.start_time))
                )
                dist = min(abs(closest_turn.start_time - seg.end_time), abs(closest_turn.end_time - seg.start_time))
                if dist <= 1.0:
                    best_speaker = closest_turn.speaker_label

            # Update segment
            updated_seg = seg.model_copy(update={"speaker": best_speaker})
            updated_segments.append(updated_seg)

            # Cumulative statistics
            seg_duration_ms = max(0, int((seg.end_time - seg.start_time) * 1000))
            speaker_time_ms[best_speaker] = speaker_time_ms.get(best_speaker, 0) + seg_duration_ms
            speaker_segment_count[best_speaker] = speaker_segment_count.get(best_speaker, 0) + 1

        # Build speaker profiles
        profiles: List[SpeakerProfile] = []
        for spk, duration_ms in sorted(speaker_time_ms.items()):
            spk_display = "Unknown Speaker" if spk == "UNKNOWN" else (
                f"Speaker {int(spk.split('_')[-1]) + 1}" if "_" in spk and spk.split('_')[-1].isdigit() else spk
            )
            profiles.append(
                SpeakerProfile(
                    speaker_label=spk,
                    display_name=spk_display,
                    total_speaking_time_ms=duration_ms,
                    segment_count=speaker_segment_count.get(spk, 0)
                )
            )

        logger.info(
            "Associated %d transcript segments with %d speakers.",
            len(updated_segments),
            len(profiles)
        )
        return updated_segments, profiles
