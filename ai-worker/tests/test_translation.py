import pytest
from uuid import uuid4
from app.providers.base_provider import TranscriptionSegment
from app.translation.engine import DictionaryTranslationProvider, TranslationEngineFactory
from app.translation.models import SupportedLanguage, TranslatedSegment
from app.translation.pipeline import TranslationPipeline
from app.translation.provider import MockTranslationProvider
from app.translation.quality import TranslationQualityChecker


class TestTranslationProvider:

    def test_supported_languages(self):
        assert SupportedLanguage.is_supported("en") is True
        assert SupportedLanguage.is_supported("hi") is True
        assert SupportedLanguage.is_supported("mr") is True
        assert SupportedLanguage.is_supported("es") is True
        assert SupportedLanguage.is_supported("xyz") is False
        assert SupportedLanguage.is_supported("") is False

    def test_mock_provider(self):
        provider = MockTranslationProvider()
        res = provider.translate("Hello world", "en", "hi")
        assert "[HI]" in res
        assert "Hello world" in res

        # Identity when source == target
        assert provider.translate("Hello world", "en", "en") == "Hello world"
        assert provider.translate("", "en", "hi") == ""

    def test_dictionary_provider_hindi(self):
        provider = DictionaryTranslationProvider()
        text = "Welcome to the video presentation on speech recognition."
        translated = provider.translate(text, "en", "hi")
        assert translated != ""
        # Check that Hindi words or Devanagari characters were inserted
        assert any(ord(char) >= 0x0900 and ord(char) <= 0x097F for char in translated)

    def test_dictionary_provider_marathi(self):
        provider = DictionaryTranslationProvider()
        text = "Welcome to the video presentation on speech recognition."
        translated = provider.translate(text, "en", "mr")
        assert translated != ""
        # Check that Marathi words or Devanagari characters were inserted
        assert any(ord(char) >= 0x0900 and ord(char) <= 0x097F for char in translated)

    def test_factory_creates_engine(self):
        engine = TranslationEngineFactory.create_engine("dictionary")
        assert isinstance(engine, DictionaryTranslationProvider)


class TestTranslationQualityChecker:

    def test_quality_normal_hindi(self):
        report = TranslationQualityChecker.evaluate(
            source_text="Welcome to speech recognition",
            translated_text="भाषण पहचान में आपका स्वागत है",
            target_lang="hi"
        )
        assert report.is_non_empty is True
        assert report.has_script_integrity is True
        assert report.estimated_quality_score >= 0.8

    def test_quality_empty_text(self):
        report = TranslationQualityChecker.evaluate(
            source_text="Welcome",
            translated_text="",
            target_lang="hi"
        )
        assert report.is_non_empty is False
        assert report.estimated_quality_score == 0.0

    def test_quality_extreme_mismatch(self):
        report = TranslationQualityChecker.evaluate(
            source_text="Hi",
            translated_text="This is an extremely long and hallucinated sentence that repeats itself over and over again without stopping.",
            target_lang="en"
        )
        assert report.estimated_quality_score < 0.8


class TestTranslationPipeline:

    def test_pipeline_preserves_timestamps_and_speakers(self):
        job_id = uuid4()
        segments = [
            TranscriptionSegment(
                id=0,
                start_time=0.0,
                end_time=3.5,
                text="Welcome to the video presentation.",
                confidence=0.98,
                speaker="SPEAKER_00"
            ),
            TranscriptionSegment(
                id=1,
                start_time=3.5,
                end_time=7.2,
                text="Today we will discuss speech recognition architectures.",
                confidence=0.95,
                speaker="SPEAKER_01"
            )
        ]

        pipeline = TranslationPipeline(provider=DictionaryTranslationProvider())
        result = pipeline.translate_segments(
            job_id=job_id,
            segments=segments,
            source_lang="en",
            target_lang="hi"
        )

        assert result.job_id == job_id
        assert result.target_language == "hi"
        assert len(result.segments) == 2

        # Strictly verify timestamps are preserved
        assert result.segments[0].start_time == 0.0
        assert result.segments[0].end_time == 3.5
        assert result.segments[0].speaker_label == "SPEAKER_00"
        assert result.segments[0].segment_index == 0
        assert len(result.segments[0].translated_text) > 0

        assert result.segments[1].start_time == 3.5
        assert result.segments[1].end_time == 7.2
        assert result.segments[1].speaker_label == "SPEAKER_01"
        assert result.segments[1].segment_index == 1

        assert len(result.full_text) > 0
        assert result.quality_score >= 0.7

    def test_pipeline_multilingual_all(self):
        job_id = uuid4()
        segments = [
            TranscriptionSegment(
                id=0,
                start_time=1.0,
                end_time=4.0,
                text="Speech recognition system architecture.",
                confidence=0.96,
                speaker="SPEAKER_00"
            )
        ]

        pipeline = TranslationPipeline(provider=DictionaryTranslationProvider())
        results = pipeline.translate_all(
            job_id=job_id,
            segments=segments,
            source_lang="en",
            target_languages=["hi", "mr"]
        )

        assert "hi" in results
        assert "mr" in results
        assert results["hi"].segments[0].start_time == 1.0
        assert results["mr"].segments[0].start_time == 1.0
        assert results["hi"].segments[0].speaker_label == "SPEAKER_00"
        assert results["mr"].segments[0].speaker_label == "SPEAKER_00"
