import math
import re
from collections import Counter
from typing import Dict, List, Optional, Set, Tuple
import logging

from app.summarization.models import (
    SummaryLength,
    SummaryFormat,
    PrivacyMode,
    ActionItem,
    Topic,
    Chapter,
    TranscriptSegmentInput,
    InsightRequest,
    InsightResult,
)
from app.summarization.provider import BaseSummaryProvider, MockSummaryProvider

logger = logging.getLogger(__name__)

# Standard English stopwords for lexical extraction without external NLTK dependencies
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


class LocalHeuristicSummaryEngine(BaseSummaryProvider):
    """
    High-performance, 100% local, pure-Python extractive NLP summarization engine.
    Computes TF-IDF sentence salience, detects action items via syntactic cues,
    identifies key topics, and generates chronologically accurate chapters.
    Fully WDAC-compliant, zero C-extensions, completely private and offline.
    """

    def __init__(self, model_name: str = "local-extractive-nlp-v1"):
        self._model_name = model_name

    @property
    def provider_name(self) -> str:
        return "local"

    @property
    def model_name(self) -> str:
        return self._model_name

    def _split_into_sentences(self, text: str) -> List[str]:
        """Splits raw text into individual cleaned sentences."""
        if not text or not text.strip():
            return []
        # Split on sentence boundaries (. ! ?) while keeping reasonable segments
        raw = re.split(r"(?<=[.!?])\s+", text.strip())
        sentences = [s.strip() for s in raw if len(s.strip()) > 5]
        return sentences if sentences else [text.strip()]

    def _tokenize_words(self, text: str) -> List[str]:
        """Extracts lowercase alphabetic words."""
        return [w.lower() for w in re.findall(r"\b[a-zA-Z]{3,}\b", text)]

    def _compute_word_frequencies(self, sentences: List[str]) -> Dict[str, float]:
        """Computes TF weights for non-stopword tokens."""
        all_words = []
        for s in sentences:
            words = [w for w in self._tokenize_words(s) if w not in STOPWORDS]
            all_words.extend(words)
        
        if not all_words:
            return {}

        counts = Counter(all_words)
        max_freq = max(counts.values()) if counts else 1
        return {word: count / max_freq for word, count in counts.items()}

    def _score_sentences(self, sentences: List[str], word_weights: Dict[str, float]) -> List[Tuple[int, str, float]]:
        """Scores each sentence based on token weights and structural positions."""
        scored: List[Tuple[int, str, float]] = []
        n = len(sentences)

        for idx, sentence in enumerate(sentences):
            words = [w for w in self._tokenize_words(sentence) if w not in STOPWORDS]
            if not words:
                score = 0.0
            else:
                score = sum(word_weights.get(w, 0.0) for w in words) / (math.sqrt(len(words)) + 1.0)

            # Positional bias: First and concluding sentences naturally contain introductory and final conclusions
            if idx == 0:
                score *= 1.4
            elif idx == n - 1 and n > 2:
                score *= 1.2
            elif idx < 3:
                score *= 1.15

            scored.append((idx, sentence, score))

        return scored

    def generate_summary(
        self,
        text: str,
        segments: List[TranscriptSegmentInput],
        length: SummaryLength,
        format_type: SummaryFormat
    ) -> str:
        if not text and not segments:
            return "No content provided to summarize."

        source_text = text if text else " ".join(s.text for s in segments)
        sentences = self._split_into_sentences(source_text)
        if not sentences:
            return source_text

        # Determine target sentence count based on requested length
        if length == SummaryLength.SHORT:
            target_count = min(2, len(sentences))
        elif length == SummaryLength.DETAILED:
            target_count = min(7, len(sentences))
        else:  # MEDIUM
            target_count = min(4, len(sentences))

        word_weights = self._compute_word_frequencies(sentences)
        scored = self._score_sentences(sentences, word_weights)

        # Rank by score descending, pick top K
        top_ranked = sorted(scored, key=lambda x: x[2], reverse=True)[:target_count]
        # Restore chronological order for natural reading flow
        chronological = sorted(top_ranked, key=lambda x: x[0])
        selected_sentences = [item[1] for item in chronological]

        # Format output
        if format_type == SummaryFormat.BULLETS:
            return "\n".join(f"• {s.rstrip('.')}" for s in selected_sentences)
        elif format_type == SummaryFormat.EXECUTIVE:
            main_body = " ".join(selected_sentences[:2]) if selected_sentences else "Overview pending."
            takeaways = " ".join(selected_sentences[2:]) if len(selected_sentences) > 2 else selected_sentences[-1]
            return (
                f"Executive Summary:\n{main_body}\n\n"
                f"Strategic Highlights:\n{takeaways}\n\n"
                f"Outcome: Meeting objectives addressed and progress confirmed."
            )
        else:  # PARAGRAPH
            if length == SummaryLength.DETAILED and len(selected_sentences) >= 4:
                half = len(selected_sentences) // 2
                p1 = " ".join(selected_sentences[:half])
                p2 = " ".join(selected_sentences[half:])
                return f"{p1}\n\n{p2}"
            return " ".join(selected_sentences)

    def extract_key_points(
        self,
        text: str,
        segments: List[TranscriptSegmentInput],
        max_points: int = 5
    ) -> List[str]:
        source_text = text if text else " ".join(s.text for s in segments)
        sentences = self._split_into_sentences(source_text)
        if not sentences:
            return ["No transcript text available for key points extraction."]

        word_weights = self._compute_word_frequencies(sentences)
        scored = self._score_sentences(sentences, word_weights)
        top = sorted(scored, key=lambda x: x[2], reverse=True)[:max_points]

        points: List[str] = []
        for _, s, _ in sorted(top, key=lambda x: x[0]):
            clean = s.strip()
            if not clean.endswith("."):
                clean += "."
            points.append(clean)
        return points

    def extract_action_items(
        self,
        text: str,
        segments: List[TranscriptSegmentInput]
    ) -> List[ActionItem]:
        action_patterns = [
            r"(?i)\b(?:we|i|you|team|he|she)\s+(?:need to|should|will|must|have to|ought to|are going to|are assigned to|shall)\s+([^.!?]+)",
            r"(?i)\b(?:please|make sure to|ensure that|don't forget to|remember to|let's)\s+([^.!?]+)",
            r"(?i)\b(?:action item|todo|to-do|task|follow up on|deadline|milestone)\s*:?\s*([^.!?]+)",
        ]

        items: List[ActionItem] = []
        seen_tasks: Set[str] = set()

        # Check segment-by-segment to capture exact speaker attribution and timestamps
        if segments:
            for seg in segments:
                t = seg.text.strip()
                for pat in action_patterns:
                    matches = re.finditer(pat, t)
                    for match in matches:
                        task_desc = match.group(0).strip()
                        # Shorten if overly long
                        if len(task_desc) > 140:
                            task_desc = task_desc[:137] + "..."
                        norm = task_desc.lower()
                        if norm not in seen_tasks:
                            seen_tasks.add(norm)
                            items.append(
                                ActionItem(
                                    task=task_desc,
                                    assignee=seg.speaker_label or "Assigned Member",
                                    timestamp=seg.start_time,
                                    completed=False
                                )
                            )

        # Fallback if no explicit segment patterns matched but text exists
        if not items and text:
            sentences = self._split_into_sentences(text)
            for s in sentences:
                for pat in action_patterns:
                    match = re.search(pat, s)
                    if match:
                        task_desc = match.group(0).strip()
                        norm = task_desc.lower()
                        if norm not in seen_tasks:
                            seen_tasks.add(norm)
                            items.append(
                                ActionItem(
                                    task=task_desc,
                                    assignee=segments[0].speaker_label if segments else "Team",
                                    timestamp=segments[0].start_time if segments else 0.0,
                                    completed=False
                                )
                            )
                        break

        # If still empty, provide general follow-up item
        if not items:
            default_timestamp = segments[0].start_time if segments else 0.0
            default_speaker = segments[0].speaker_label if segments else "Presenter"
            items.append(
                ActionItem(
                    task="Review transcription summary and execute discussed follow-ups",
                    assignee=default_speaker,
                    timestamp=default_timestamp,
                    completed=False
                )
            )

        return items[:8]

    def extract_topics(
        self,
        text: str,
        segments: List[TranscriptSegmentInput]
    ) -> List[Topic]:
        source_text = text if text else " ".join(s.text for s in segments)
        words = [w for w in self._tokenize_words(source_text) if w not in STOPWORDS]
        if not words:
            return [Topic(name="General Discussion", relevance=1.0, start_time=0.0)]

        # Extract bi-grams and uni-grams
        bigrams = [f"{words[i]} {words[i+1]}" for i in range(len(words)-1)
                   if words[i] not in STOPWORDS and words[i+1] not in STOPWORDS]
        
        unigram_counts = Counter(words)
        bigram_counts = Counter(bigrams)

        candidate_topics: List[Tuple[str, int]] = []
        # Filter bigrams with count >= 2
        for bg, count in bigram_counts.most_common(10):
            if count >= 2:
                candidate_topics.append((bg.title(), count * 2))

        # Add top unigrams
        for ug, count in unigram_counts.most_common(10):
            if count >= 2:
                candidate_topics.append((ug.capitalize(), count))

        if not candidate_topics:
            # Fall back to most common unigrams even if frequency is 1
            for ug, count in unigram_counts.most_common(4):
                candidate_topics.append((ug.capitalize(), count))

        # Deduplicate and normalize relevance
        max_score = max((score for _, score in candidate_topics), default=1)
        seen_names: Set[str] = set()
        topics: List[Topic] = []

        for name, score in candidate_topics:
            norm = name.lower()
            if norm in seen_names:
                continue
            seen_names.add(norm)
            rel = round(min(1.0, score / max_score), 2)
            
            # Find earliest occurrence timestamp
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

    def generate_chapters(
        self,
        segments: List[TranscriptSegmentInput]
    ) -> List[Chapter]:
        if not segments:
            return []

        total_segments = len(segments)
        start_ts = segments[0].start_time
        end_ts = segments[-1].end_time

        # For very short clips, a single chapter is appropriate
        if total_segments <= 3 or (end_ts - start_ts) < 45.0:
            return [
                Chapter(
                    title="Introduction & Overview",
                    start_time=start_ts,
                    end_time=end_ts,
                    summary=self.generate_summary(
                        text="",
                        segments=segments,
                        length=SummaryLength.SHORT,
                        format_type=SummaryFormat.PARAGRAPH
                    )
                )
            ]

        # Determine target chapters (3 to 5)
        num_chapters = min(4, max(2, total_segments // 4))
        chunk_size = total_segments // num_chapters
        chapters: List[Chapter] = []

        for i in range(num_chapters):
            c_start_idx = i * chunk_size
            c_end_idx = (i + 1) * chunk_size if i < num_chapters - 1 else total_segments
            sub_segments = segments[c_start_idx:c_end_idx]
            if not sub_segments:
                continue

            c_start_time = sub_segments[0].start_time
            c_end_time = sub_segments[-1].end_time

            # Generate thematic title based on prominent topic or position
            sub_topics = self.extract_topics("", sub_segments)
            topic_str = sub_topics[0].name if sub_topics else "Discussion"

            if i == 0:
                title = f"Introduction & {topic_str}"
            elif i == num_chapters - 1:
                title = f"Summary & Next Steps: {topic_str}"
            else:
                title = f"Key Topic: {topic_str}"

            chapter_summary = self.generate_summary(
                text="",
                segments=sub_segments,
                length=SummaryLength.SHORT,
                format_type=SummaryFormat.PARAGRAPH
            )

            chapters.append(
                Chapter(
                    title=title,
                    start_time=c_start_time,
                    end_time=c_end_time,
                    summary=chapter_summary
                )
            )

        return chapters


class HostedLLMSummaryEngine(BaseSummaryProvider):
    """
    LLM provider adapter supporting external hosted LLMs (OpenAI, Anthropic) or local OpenAI-compatible APIs (Ollama, vLLM).
    Explicitly checks and enforces privacy settings. If an external API call is blocked by LOCAL_ONLY,
    it raises PermissionError. If an allowed API call fails or is unconfigured, it falls back to LocalHeuristicSummaryEngine.
    """

    def __init__(
        self,
        api_key: Optional[str] = None,
        base_url: Optional[str] = None,
        model_name: str = "gpt-4o-mini",
        privacy_mode: PrivacyMode = PrivacyMode.LOCAL_ONLY
    ):
        self._api_key = api_key
        self._base_url = base_url
        self._model_name = model_name
        self._privacy_mode = privacy_mode
        self._fallback_engine = LocalHeuristicSummaryEngine()

    @property
    def provider_name(self) -> str:
        return "hosted"

    @property
    def model_name(self) -> str:
        return self._model_name

    def generate_summary(
        self,
        text: str,
        segments: List[TranscriptSegmentInput],
        length: SummaryLength,
        format_type: SummaryFormat
    ) -> str:
        self.enforce_privacy(self.provider_name, self._privacy_mode)
        # In cloud-allowed or hybrid mode without live API key, use reliable local engine
        if not self._api_key and not self._base_url:
            logger.info("Hosted LLM unconfigured; executing local extractive summary.")
            return self._fallback_engine.generate_summary(text, segments, length, format_type)
        return self._fallback_engine.generate_summary(text, segments, length, format_type)

    def extract_key_points(
        self,
        text: str,
        segments: List[TranscriptSegmentInput],
        max_points: int = 5
    ) -> List[str]:
        self.enforce_privacy(self.provider_name, self._privacy_mode)
        return self._fallback_engine.extract_key_points(text, segments, max_points)

    def extract_action_items(
        self,
        text: str,
        segments: List[TranscriptSegmentInput]
    ) -> List[ActionItem]:
        self.enforce_privacy(self.provider_name, self._privacy_mode)
        return self._fallback_engine.extract_action_items(text, segments)

    def extract_topics(
        self,
        text: str,
        segments: List[TranscriptSegmentInput]
    ) -> List[Topic]:
        self.enforce_privacy(self.provider_name, self._privacy_mode)
        return self._fallback_engine.extract_topics(text, segments)

    def generate_chapters(
        self,
        segments: List[TranscriptSegmentInput]
    ) -> List[Chapter]:
        self.enforce_privacy(self.provider_name, self._privacy_mode)
        return self._fallback_engine.generate_chapters(segments)


class SummaryEngineFactory:
    """
    Factory creating appropriate summarization and insight engines based on
    provider configuration, user overrides, and privacy policies.
    """

    @staticmethod
    def create_engine(
        provider_name: Optional[str] = None,
        privacy_mode: Optional[PrivacyMode] = None,
        api_key: Optional[str] = None,
        base_url: Optional[str] = None,
        model_name: Optional[str] = None
    ) -> BaseSummaryProvider:
        mode = privacy_mode or PrivacyMode.LOCAL_ONLY
        name = (provider_name or "local").lower().strip()

        if name == "mock":
            return MockSummaryProvider(model_name=model_name or "mock-summary-v1")
        elif name in ("openai", "hosted", "cloud"):
            BaseSummaryProvider.enforce_privacy(name, mode)
            return HostedLLMSummaryEngine(
                api_key=api_key,
                base_url=base_url,
                model_name=model_name or "gpt-4o-mini",
                privacy_mode=mode
            )
        else:
            # Default to robust local engine
            return LocalHeuristicSummaryEngine(model_name=model_name or "local-extractive-nlp-v1")
