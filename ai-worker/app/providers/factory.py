import logging
from typing import Optional
from app.providers.base_provider import ASRProvider
from app.providers.device_detector import DeviceDetector
from app.providers.faster_whisper_provider import FasterWhisperProvider
from app.providers.mock_provider import MockASRProvider

logger = logging.getLogger(__name__)


class ASRProviderFactory:
    """Instantiates and configures appropriate ASR provider based on requested parameters and hardware."""

    @classmethod
    def create_provider(
        cls,
        engine: str = "faster-whisper",
        model_size: str = "small",
        device: str = "auto",
        compute_type: Optional[str] = None
    ) -> ASRProvider:
        # Detect hardware capabilities
        hardware = DeviceDetector.get_optimal_device(device)
        resolved_device = hardware.device
        resolved_compute = compute_type or hardware.recommended_compute_type

        logger.info(
            "Creating ASR provider [engine=%s, model=%s, device=%s, compute=%s]",
            engine,
            model_size,
            resolved_device,
            resolved_compute
        )

        if engine.lower() in ("mock", "mock-provider", "stub", "test"):
            provider = MockASRProvider(
                model_size=model_size,
                device=resolved_device,
                compute_type=resolved_compute
            )
            return provider

        # Attempt to load faster-whisper provider
        try:
            provider = FasterWhisperProvider(
                model_size=model_size,
                device=resolved_device,
                compute_type=resolved_compute
            )
            return provider
        except Exception as err:
            logger.warning(
                "Unable to initialize faster-whisper provider (%s). Falling back to MockASRProvider for runtime resilience.",
                err
            )
            return MockASRProvider(
                model_size=model_size,
                device="cpu",
                compute_type="int8"
            )
