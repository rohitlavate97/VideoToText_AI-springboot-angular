import math
import re
from collections import Counter
from typing import Dict, List, Optional, Set, Tuple

from app.summarization.models import (
    ActionItem,
    Topic,
    TranscriptSegmentInput,
)

STOPWORDS: Set[str] = {
    "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't",
    "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by", "can",
    "can't", "cannot", "could", "couldn't", "did", "didn't", "do", "does", "doesn't", "doing", "don't",
    "down", "during", "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't", "have",
    "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here", "here's", "hers", "herself", "him",
    "himself", "his", "how", "how's", "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is", "isn't",
    "it", "it's", "its", "itself", "let's", "me", "more", "most", "mustn't", "my", "myself", "no", "nor",
    "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves", "out",
    "over", "own", "same", "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "so", "some",
    "such", "than", "that", "that's", "the", "their", "theirs", "them", "themselves", "then", "there",
    "there's", "these", "they", "they'd", "they'll", "they're", "they've", "this", "those", "through", "to",
    "too", "under", "until", "up", "very", "was", "wasn't", "we", "we'd", "we'll", "we're", "we've", "were",
    "weren't", "what", "what's", "when", "when's", "where", "where's", "which", "while", "who", "who's",
    "whom", "why", "why's", "with", "won't", "would", "wouldn't", "you", "you'd", "you'll", "you're", "you've",
    "your", "yours", "yourself", "yourselves", "also", "just", "like", "okay", "um", "uh", "yeah", "well"
}


class KeyPointExtractor:
    """Extracts distinctive, high-salience key points and takeaways from transcript content."""

    @staticmethod
    def extract(text: str, segments: List[TranscriptSegmentInput], max_points: int = 5) -> List[str]:
        source = text if text else " ".join(s.text for s in segments)
        if not source or not source.strip():
            return ["No content available for key takeaways."]

        raw_sentences = re.split(r"(?<=[.!?])\s+", source.strip())
        sentences = [s.strip() for s in raw_sentences if len(s.strip()) > 8]
        if not sentences:
            return [source.strip()]

        # Tokenize and score
        all_words = []
        for s in sentences:
            words = [w.lower() for w in re.findall(r"\b[a-zA-Z]{3,}\b", s) if w.lower() not in STOPWORDS]
            all_words.extend(words)

        counts = Counter(all_words)
        max_c = max(counts.values()) if counts else 1
        weights = {w: c / max_c for w, c in counts.items()}

        scored = []
        for idx, s in enumerate(sentences):
            words = [w.lower() for w in re.findall(r"\b[a-zA-Z]{3,}\b", s) if w.lower() not in STOPWORDS]
            score = sum(weights.get(w, 0.0) for w in words) / (math.sqrt(len(words)) + 1.0) if words else 0.0
            if idx == 0:
                score *= 1.3
            elif idx == len(sentences) - 1:
                score *= 1.2
            scored.append((idx, s, score))

        top = sorted(scored, key=lambda x: x[2], reverse=True)[:max_points]
        chronological = sorted(top, key=lambda x: x[0])

        results = []
        for _, s, _ in chronological:
            clean = s.rstrip(".!?") + "."
            results.append(clean)
        return results


class ActionItemExtractor:
    """Synthesizes actionable commitments, obligations, and deadlines with speaker timestamps."""

    PATTERNS = [
        r"(?i)\b(?:we|i|you|team|he|she)\s+(?:need to|should|will|must|have to|ought to|are going to|are assigned to|shall)\s+([^.!?]+)",
        r"(?i)\b(?:please|make sure to|ensure that|don't forget to|remember to|let's)\s+([^.!?]+)",
        r"(?i)\b(?:action item|todo|to-do|task|follow up on|deadline|milestone)\s*:?\s*([^.!?]+)",
    ]

    @classmethod
    def extract(cls, text: str, segments: List[TranscriptSegmentInput]) -> List[ActionItem]:
        items: List[ActionItem] = []
        seen: Set[str] = set()

        if segments:
            for seg in segments:
                t = seg.text.strip()
                for pat in cls.PATTERNS:
                    for match in re.finditer(pat, t):
                        desc = match.group(0).strip()
                        if len(desc) > 130:
                            desc = desc[:127] + "..."
                        norm = desc.lower()
                        if norm not in seen:
                            seen.add(norm)
                            items.append(
                                ActionItem(
                                    task=desc,
                                    assignee=seg.speaker_label or "Participant",
                                    timestamp=seg.start_time,
                                    completed=False
                                )
                            )

        if not items and text:
            sentences = [s.strip() for s in re.split(r"(?<=[.!?])\s+", text) if s.strip()]
            for s in sentences:
                for pat in cls.PATTERNS:
                    match = re.search(pat, s)
                    if match:
                        desc = match.group(0).strip()
                        norm = desc.lower()
                        if norm not in seen:
                            seen.add(norm)
                            items.append(
                                ActionItem(
                                    task=desc,
                                    assignee=segments[0].speaker_label if segments else "Team",
                                    timestamp=segments[0].start_time if segments else 0.0,
                                    completed=False
                                )
                            )
                        break

        if not items:
            default_speaker = segments[0].speaker_label if segments else "Lead"
            default_time = segments[0].start_time if segments else 0.0
            items.append(
                ActionItem(
                    task="Review transcription summary and execute discussed follow-ups",
                    assignee=default_speaker,
                    timestamp=default_time,
                    completed=False
                )
            )

        return items[:8]


class TopicIdentifier:
    """Discovers dominant topics, computes relevance, and localizes first appearance timestamps."""

    @staticmethod
    def identify(text: str, segments: List[TranscriptSegmentInput]) -> List[Topic]:
        source = text if text else " ".join(s.text for s in segments)
        words = [w.lower() for w in re.findall(r"\b[a-zA-Z]{3,}\b", source) if w.lower() not in STOPWORDS]
        if not words:
            return [Topic(name="General Discussion", relevance=1.0, start_time=0.0)]

        bigrams = [f"{words[i]} {words[i+1]}" for i in range(len(words)-1)
                   if words[i] not in STOPWORDS and words[i+1] not in STOPWORDS]
        
        unigram_counts = Counter(words)
        bigram_counts = Counter(bigrams)

        candidate_topics: List[Tuple[str, int]] = []
        for bg, count in bigram_counts.most_common(8):
            if count >= 2:
                candidate_topics.append((bg.title(), count * 2))

        for ug, count in unigram_counts.most_common(8):
            if count >= 2:
                candidate_topics.append((ug.capitalize(), count))

        if not candidate_topics:
            for ug, count in unigram_counts.most_common(4):
                candidate_topics.append((ug.capitalize(), count))

        max_score = max((score for _, score in candidate_topics), default=1)
        seen_names: Set[str] = set()
        topics: List[Topic] = []

        for name, score in candidate_topics:
            norm = name.lower()
            if norm in seen_names:
                continue
            seen_names.add(norm)
            rel = round(min(1.0, score / max_score), 2)

            earliest_time = 0.0
            if segments:
                for seg in segments:
                    if re.search(r"\b" + re.escape(norm) + r"\b", seg.text.lower()):
                        earliest_time = seg.start_time
                        break

            topics.append(Topic(name=name, relevance=rel, start_time=earliest_time))
            if len(topics) >= 6:
                break

        return sorted(topics, key=lambda t: t.relevance, reverse=True)
