# ADR-002: ASR Provider Abstraction

**Status**: Accepted  
**Date**: 2026-11-16  
**Context**: Choosing how to integrate transcription engines without tight coupling.

## Decision

Implement a **provider abstraction layer** (Strategy pattern) for ASR engines in the Python AI Worker.

## Context

The application must support multiple ASR engines:
1. faster-whisper (primary, local GPU).
2. OpenAI Whisper (reference implementation).
3. Custom ASR engine (future).
4. Cloud transcription APIs (future).

Each engine has different APIs, capabilities, model formats, and supported languages. The orchestration pipeline should not need to know which engine is active.

## Design

```python
class ASRProvider(ABC):
    @abstractmethod
    def transcribe(self, audio_path: str, options: TranscriptionOptions) -> TranscriptionResult:
        ...

    @abstractmethod
    def get_provider_name(self) -> str:
        ...

    @abstractmethod
    def get_supported_languages(self) -> list[str]:
        ...

    @abstractmethod
    def supports_word_timestamps(self) -> bool:
        ...
```

A `ProviderRegistry` maps engine names to implementations. The active provider is selected per-job via configuration.

## Consequences

**Positive**:
- Swap engines without changing the pipeline.
- Benchmark different engines with identical orchestration.
- Add new engines by implementing a single interface.
- Per-job engine selection allows A/B comparison.

**Negative**:
- Lowest-common-denominator interface may not expose engine-specific features.
- Each provider adapter must handle engine-specific error modes.

**Mitigations**:
- The `TranscriptionOptions` model includes an `extra_params: dict` field for engine-specific settings.
- Provider adapters translate engine-specific errors to a common error model.
