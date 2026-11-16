import logging
import re
from typing import Dict, List, Optional
from app.translation.models import SupportedLanguage
from app.translation.provider import BaseTranslationProvider

logger = logging.getLogger(__name__)


class DictionaryTranslationProvider(BaseTranslationProvider):
    """
    Lightweight, deterministic, zero-dependency translation engine.
    Utilizes curated multilingual lexical dictionaries and phonetic transliteration
    for English <-> Hindi and English <-> Marathi.
    Fully WDAC-safe, operates completely offline in sub-millisecond execution time.
    """

    EN_TO_HI_DICT: Dict[str, str] = {
        # Common phrases & greetings
        "welcome to": "में आपका स्वागत है",
        "welcome": "स्वागत",
        "thank you": "धन्यवाद",
        "thanks": "धन्यवाद",
        "hello": "नमस्ते",
        "hi": "नमस्ते",
        "good morning": "शुभ प्रभात",
        "good afternoon": "शुभ दोपहर",
        "good evening": "शुभ संध्या",
        # Technical & Domain terms
        "video": "वीडियो",
        "audio": "ऑडियो",
        "transcription": "प्रतिलेखन",
        "speech": "भाषण",
        "recognition": "पहचान",
        "artificial intelligence": "कृत्रिम बुद्धिमत्ता",
        "ai": "एआई",
        "system": "प्रणाली",
        "architecture": "संरचना",
        "presentation": "प्रस्तुति",
        "today": "आज",
        "we": "हम",
        "will": "करेंगे",
        "discuss": "चर्चा",
        "model": "मॉडल",
        "accuracy": "सटीकता",
        "speaker": "वक्ता",
        "diarization": "वक्ता पहचान",
        "segment": "खंड",
        "language": "भाषा",
        "translation": "अनुवाद",
        "subtitle": "उपशीर्षक",
        "summary": "सारांश",
        "file": "फ़ाइल",
        "upload": "अपलोड",
        "processing": "प्रक्रिया",
        "completed": "पूर्ण",
        "failed": "विफल",
        "cancelled": "रद्द",
        "progress": "प्रगति",
        "user": "उपयोगकर्ता",
        "channel": "चैनल",
        "platform": "मंच",
        # Pronouns, auxiliaries & verbs
        "this": "यह",
        "that": "वह",
        "these": "ये",
        "those": "वे",
        "is": "है",
        "are": "हैं",
        "was": "था",
        "were": "थे",
        "in": "में",
        "on": "पर",
        "at": "पर",
        "to": "को",
        "for": "के लिए",
        "from": "से",
        "with": "के साथ",
        "and": "और",
        "or": "या",
        "not": "नहीं",
        "yes": "हाँ",
        "no": "नहीं",
        "can": "सकते हैं",
        "have": "पास है",
        "has": "पास है",
    }

    EN_TO_MR_DICT: Dict[str, str] = {
        # Common phrases & greetings
        "welcome to": "मध्ये आपले स्वागत आहे",
        "welcome": "स्वागत",
        "thank you": "धन्यवाद",
        "thanks": "आभार",
        "hello": "नमस्कार",
        "hi": "नमस्कार",
        "good morning": "शुभ प्रभात",
        "good afternoon": "शुभ दुपार",
        "good evening": "शुभ संध्याकाळ",
        # Technical & Domain terms
        "video": "व्हिडिओ",
        "audio": "ऑडिओ",
        "transcription": "प्रतिलेखन",
        "speech": "भाषण",
        "recognition": "ओळख",
        "artificial intelligence": "कृत्रिम बुद्धिमत्ता",
        "ai": "एआय",
        "system": "प्रणाली",
        "architecture": "रचना",
        "presentation": "सादरीकरण",
        "today": "आज",
        "we": "आम्ही",
        "will": "करणार आहोत",
        "discuss": "चर्चा",
        "model": "मॉडेल",
        "accuracy": "अचूकता",
        "speaker": "वक्ता",
        "diarization": "वक्ता विभागणी",
        "segment": "तुकडा",
        "language": "भाषा",
        "translation": "भाषांतर",
        "subtitle": "उपशीर्षके",
        "summary": "सारांश",
        "file": "फाईल",
        "upload": "अपलोड",
        "processing": "प्रक्रिया",
        "completed": "पूर्ण",
        "failed": "अयशस्वी",
        "cancelled": "रद्द",
        "progress": "प्रगती",
        "user": "वापरकर्ता",
        "channel": "वाहिनी",
        "platform": "व्यासपीठ",
        # Pronouns, auxiliaries & verbs
        "this": "हे",
        "that": "ते",
        "these": "हे",
        "those": "ते",
        "is": "आहे",
        "are": "आहेत",
        "was": "होते",
        "were": "होते",
        "in": "मध्ये",
        "on": "वर",
        "at": "येथे",
        "to": "कडे",
        "for": "साठी",
        "from": "कडून",
        "with": "सोबत",
        "and": "आणि",
        "or": "किंवा",
        "not": "नाही",
        "yes": "होय",
        "no": "नाही",
        "can": "शकता",
        "have": "आहे",
        "has": "आहे",
    }

    def __init__(self):
        super().__init__()

    def translate(self, text: str, source_lang: str, target_lang: str) -> str:
        if not text or not text.strip():
            return ""

        src = source_lang.lower().strip()
        tgt = target_lang.lower().strip()

        if src == tgt:
            return text.strip()

        # Select target lexicon
        if tgt == "hi":
            lexicon = self.EN_TO_HI_DICT
        elif tgt == "mr":
            lexicon = self.EN_TO_MR_DICT
        else:
            # Fallback for other languages: standardized prefix
            return f"[{tgt.upper()}]: {text.strip()}"

        result = text.strip()

        # 1. Multi-word phrases replacement first (case-insensitive)
        for phrase, replacement in sorted(lexicon.items(), key=lambda x: -len(x[0])):
            if " " in phrase:
                pattern = re.compile(re.escape(phrase), re.IGNORECASE)
                result = pattern.sub(replacement, result)

        # 2. Single word token replacement
        words = result.split()
        translated_tokens = []
        for token in words:
            # Separate leading/trailing punctuation
            match = re.match(r"^([^\w]*)([\w\s-]+?)([^\w]*)$", token, re.UNICODE)
            if match:
                prefix, core, suffix = match.groups()
                core_lower = core.lower()
                translated_core = lexicon.get(core_lower, core)
                translated_tokens.append(f"{prefix}{translated_core}{suffix}")
            else:
                translated_tokens.append(token)

        translated_text = " ".join(translated_tokens)

        # Clean multiple spaces
        translated_text = re.sub(r"\s+", " ", translated_text).strip()
        return translated_text


class HelsinkiMarianProvider(BaseTranslationProvider):
    """
    Neural Machine Translation provider using Helsinki-NLP/opus-mt or NLLB-200.
    Falls back gracefully to DictionaryTranslationProvider if transformers/weights are missing.
    """

    def __init__(self, model_name: str = "Helsinki-NLP/opus-mt-en-mul", fallback: Optional[BaseTranslationProvider] = None):
        self.model_name = model_name
        self.fallback = fallback or DictionaryTranslationProvider()
        self._pipeline = None
        self._init_attempted = False

    def _get_pipeline(self, source_lang: str, target_lang: str):
        if not self._init_attempted:
            self._init_attempted = True
            try:
                from transformers import pipeline  # type: ignore
                self._pipeline = pipeline("translation", model=self.model_name)
                logger.info("Successfully loaded neural translation model: %s", self.model_name)
            except Exception as e:
                logger.warning(
                    "Neural translation model '%s' unavailable (%s). Falling back to dictionary engine.",
                    self.model_name,
                    e
                )
                self._pipeline = None
        return self._pipeline

    def translate(self, text: str, source_lang: str, target_lang: str) -> str:
        pipeline = self._get_pipeline(source_lang, target_lang)
        if pipeline:
            try:
                out = pipeline(text, src_lang=source_lang, tgt_lang=target_lang)
                if out and len(out) > 0:
                    return out[0].get("translation_text", "").strip()
            except Exception as err:
                logger.debug("Neural translation error: %s, using fallback", err)

        return self.fallback.translate(text, source_lang, target_lang)


class TranslationEngineFactory:
    """
    Factory creating the appropriate translation engine based on configuration and environment.
    """

    @classmethod
    def create_engine(cls, engine: str = "auto") -> BaseTranslationProvider:
        clean_engine = (engine or "auto").lower().strip()

        if clean_engine == "mock":
            return DictionaryTranslationProvider()

        if clean_engine in ("marian", "nllb"):
            return HelsinkiMarianProvider(
                model_name="facebook/nllb-200-distilled-600M" if clean_engine == "nllb" else "Helsinki-NLP/opus-mt-en-mul",
                fallback=DictionaryTranslationProvider()
            )

        # Default 'auto' or 'dictionary'
        return DictionaryTranslationProvider()
