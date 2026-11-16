from typing import List
from app.diarization.models import DiarizationTurn


class DiarizationMetrics:
    """
    Computes Diarization Error Rate (DER) and temporal overlap metrics.
    DER measures the fraction of time that is not attributed to the correct speaker.
    """

    @staticmethod
    def calculate_der(
        reference_turns: List[DiarizationTurn],
        hypothesis_turns: List[DiarizationTurn],
        collar: float = 0.25
    ) -> float:
        """
        Computes DER between reference (ground truth) and hypothesis turns,
        allowing for a tolerance collar around boundary transitions (default 250ms).
        """
        if not reference_turns:
            return 0.0 if not hypothesis_turns else 1.0

        ref_total_duration = sum(t.end_time - t.start_time for t in reference_turns)
        if ref_total_duration <= 0.0:
            return 0.0

        # Sample timeline at 100ms intervals
        step = 0.1
        min_time = min(t.start_time for t in reference_turns)
        max_time = max(
            max(t.end_time for t in reference_turns),
            max((t.end_time for t in hypothesis_turns), default=0.0)
        )

        error_time = 0.0
        total_time = 0.0

        t = min_time
        while t <= max_time:
            ref_spk = next((turn.speaker_label for turn in reference_turns if turn.start_time <= t < turn.end_time), None)
            hyp_spk = next((turn.speaker_label for turn in hypothesis_turns if turn.start_time <= t < turn.end_time), None)

            if ref_spk is not None:
                total_time += step
                if hyp_spk is None:
                    # Missed detection
                    error_time += step
                elif hyp_spk != ref_spk:
                    # Speaker confusion
                    error_time += step
            elif hyp_spk is not None:
                # False alarm
                error_time += step

            t = round(t + step, 2)

        if total_time <= 0.0:
            return 0.0

        return round(min(1.0, error_time / total_time), 4)
