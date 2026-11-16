from abc import ABC, abstractmethod
from typing import List
from app.translation.models import SupportedLanguage


class BaseTranslationProvider(ABC):
    """
    Abstract Base Class for translation engines (MarianMT, NLLB-200, Cloud, Mock).
    """

    @abstractmethod
    def translate(self, text: str, source_lang: str, target_lang: str) -> str:
        """
        Translates a single text string from source_lang to target_lang.
        """
        pass

    def translate_batch(self, texts: List[str], source_lang: str, target_lang: str) -> List[str]:
        """
        Translates a batch of text strings from source_lang to target_lang.
        Default implementation iterates over single translate calls.
        """
        return [self.translate(t, source_lang, target_lang) for t in texts]

    def is_pair_supported(self, source_lang: str, target_lang: str) -> bool:
        """
        Checks whether this translation provider supports the given language pair.
        """
        return SupportedLanguage.is_supported(source_lang) and SupportedLanguage.is_supported(target_lang)


class MockTranslationProvider(BaseTranslationProvider):
    """
    Deterministic mock translation provider for rapid testing and local verification.
    Appends or prefixes target language tags while maintaining word count and structure.
    """

    def __init__(self, prefix: str = "[TRANSLATED]"):
        self.prefix = prefix

    def translate(self, text: str, source_lang: str, target_lang: str) -> str:
        if not text or not text.strip():
            return ""
        if source_lang.lower() == target_lang.lower():
            return text.strip()
        return f"{self.prefix} [{target_lang.upper()}]: {text.strip()}"

    def translate_batch(self, texts: List[str], source_lang: str, target_lang: str) -> List[str]:
        return [self.translate(t, source_lang, target_lang) for t in texts]
