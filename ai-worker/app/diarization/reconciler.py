import logging
import math
from typing import Dict, List, Optional, Tuple
from app.diarization.models import DiarizationTurn, SpeakerProfile
from app.providers.base_provider import TranscriptionSegment

logger = logging.getLogger(__name__)


def _cosine_similarity(vec_a: List[float], vec_b: List[float]) -> float:
    """Computes cosine similarity between two 1D vectors."""
    if not vec_a or not vec_b or len(vec_a) != len(vec_b):
        return 0.0
    dot = sum(a * b for a, b in zip(vec_a, vec_b))
    norm_a = math.sqrt(sum(a * a for a in vec_a))
    norm_b = math.sqrt(sum(b * b for b in vec_b))
    if norm_a <= 0.0 or norm_b <= 0.0:
        return 0.0
    return dot / (norm_a * norm_b)


def _average_embeddings(vec_a: List[float], vec_b: List[float]) -> List[float]:
    """Averages and L2-normalizes two embedding vectors."""
    avg = [(a + b) / 2.0 for a, b in zip(vec_a, vec_b)]
    norm = math.sqrt(sum(x * x for x in avg))
    if norm > 0.0:
        return [round(x / norm, 5) for x in avg]
    return avg


class SpeakerReconciler:
    """
    Reconciles speaker identities across multiple audio chunks:
    - Maintains a global speaker inventory with centroid acoustic embedding vectors.
    - Computes cosine similarity between chunk speaker embeddings and global centroids.
    - Re-labels chunk speakers to unified global identities when similarity >= threshold (default 0.70).
    - Allocates new global identities for novel speakers appearing in later chunks.
    """

    def __init__(self, similarity_threshold: float = 0.70):
        self.similarity_threshold = similarity_threshold
        self.global_speakers: Dict[str, SpeakerProfile] = {}
        self._next_speaker_index = 0

    def reconcile_chunk(
        self,
        chunk_index: int,
        turns: List[DiarizationTurn],
        segments: List[TranscriptionSegment],
        speaker_profiles: List[SpeakerProfile]
    ) -> Tuple[List[DiarizationTurn], List[TranscriptionSegment], List[SpeakerProfile]]:
        """
        Reconciles speaker IDs for a single chunk against the global inventory.
        Returns:
            Tuple[reconciled_turns, reconciled_segments, current_global_profiles]
        """
        if not turns and not segments:
            return turns, segments, list(self.global_speakers.values())

        # Map from local chunk speaker label -> global speaker label
        local_to_global: Dict[str, str] = {}

        for profile in speaker_profiles:
            local_label = profile.speaker_label
            if local_label == "UNKNOWN":
                local_to_global["UNKNOWN"] = "UNKNOWN"
                continue

            local_emb = profile.embedding
            best_match_id = None
            highest_sim = -1.0

            if local_emb is not None:
                for g_id, g_profile in self.global_speakers.items():
                    if g_profile.embedding is not None:
                        sim = _cosine_similarity(local_emb, g_profile.embedding)
                        if sim > highest_sim:
                            highest_sim = sim
                            best_match_id = g_id

            if best_match_id is not None and highest_sim >= self.similarity_threshold:
                logger.debug(
                    "Chunk %d: Reconciled local '%s' -> global '%s' (similarity: %.3f)",
                    chunk_index,
                    local_label,
                    best_match_id,
                    highest_sim
                )
                local_to_global[local_label] = best_match_id
                # Update global speaker stats & running centroid
                g_spk = self.global_speakers[best_match_id]
                g_spk.total_speaking_time_ms += profile.total_speaking_time_ms
                g_spk.segment_count += profile.segment_count
                if local_emb is not None and g_spk.embedding is not None:
                    g_spk.embedding = _average_embeddings(g_spk.embedding, local_emb)
            else:
                # New global speaker
                new_global_id = f"SPEAKER_{self._next_speaker_index:02d}"
                self._next_speaker_index += 1
                display_num = self._next_speaker_index
                display_name = f"Speaker {display_num}"

                logger.debug(
                    "Chunk %d: Discovered new global speaker '%s' (local '%s')",
                    chunk_index,
                    new_global_id,
                    local_label
                )
                local_to_global[local_label] = new_global_id
                self.global_speakers[new_global_id] = SpeakerProfile(
                    speaker_label=new_global_id,
                    display_name=display_name,
                    total_speaking_time_ms=profile.total_speaking_time_ms,
                    segment_count=profile.segment_count,
                    embedding=profile.embedding
                )

        # Re-label turns
        reconciled_turns: List[DiarizationTurn] = []
        for turn in turns:
            g_lbl = local_to_global.get(turn.speaker_label, turn.speaker_label)
            reconciled_turns.append(turn.model_copy(update={"speaker_label": g_lbl}))

        # Re-label segments
        reconciled_segments: List[TranscriptionSegment] = []
        for seg in segments:
            g_lbl = local_to_global.get(seg.speaker, seg.speaker)
            reconciled_segments.append(seg.model_copy(update={"speaker": g_lbl}))

        return reconciled_turns, reconciled_segments, list(self.global_speakers.values())
