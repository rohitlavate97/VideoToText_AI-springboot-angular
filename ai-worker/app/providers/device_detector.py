import logging
import os
import shutil
import subprocess
from typing import Dict, Optional, Tuple
from pydantic import BaseModel

logger = logging.getLogger(__name__)


class DeviceCapability(BaseModel):
    """Hardware capability report for model inference."""
    device: str  # 'cuda' or 'cpu'
    is_gpu: bool
    device_name: str
    vram_total_mb: Optional[int] = None
    vram_free_mb: Optional[int] = None
    recommended_compute_type: str
    cpu_cores: int


class DeviceDetector:
    """Detects available GPU acceleration (CUDA) and configures optimal compute precision or CPU fallback."""

    @staticmethod
    def detect_nvidia_smi() -> Tuple[bool, Optional[str], Optional[int], Optional[int]]:
        """Queries nvidia-smi command if available on host system."""
        if not shutil.which("nvidia-smi"):
            return False, None, None, None

        try:
            cmd = [
                "nvidia-smi",
                "--query-gpu=name,memory.total,memory.free",
                "--format=csv,noheader,nounits"
            ]
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=3)
            if result.returncode == 0 and result.stdout.strip():
                lines = result.stdout.strip().split("\n")
                if lines:
                    parts = [p.strip() for p in lines[0].split(",")]
                    name = parts[0]
                    total_mb = int(parts[1]) if len(parts) > 1 and parts[1].isdigit() else None
                    free_mb = int(parts[2]) if len(parts) > 2 and parts[2].isdigit() else None
                    return True, name, total_mb, free_mb
        except Exception as e:
            logger.debug("nvidia-smi check returned error: %s", e)

        return False, None, None, None

    @classmethod
    def get_optimal_device(cls, requested_device: str = "auto") -> DeviceCapability:
        """Determines best hardware execution target (CUDA or CPU fallback)."""
        cpu_cores = os.cpu_count() or 4

        # Explicit CPU request overrides auto-detection
        if requested_device.lower() == "cpu":
            return DeviceCapability(
                device="cpu",
                is_gpu=False,
                device_name="Host CPU",
                recommended_compute_type="int8",
                cpu_cores=cpu_cores
            )

        # Check PyTorch CUDA if available
        try:
            import torch
            if torch.cuda.is_available():
                name = torch.cuda.get_device_name(0)
                total_vram = int(torch.cuda.get_device_properties(0).total_memory / (1024 * 1024))
                compute_type = "float16" if total_vram >= 6000 else "int8_float16"
                logger.info("CUDA GPU detected via PyTorch: %s (%d MB VRAM)", name, total_vram)
                return DeviceCapability(
                    device="cuda",
                    is_gpu=True,
                    device_name=name,
                    vram_total_mb=total_vram,
                    recommended_compute_type=compute_type,
                    cpu_cores=cpu_cores
                )
        except Exception:
            pass

        # Check CTranslate2 CUDA if available
        try:
            import ctranslate2
            if ctranslate2.get_cuda_device_count() > 0:
                logger.info("CUDA GPU detected via CTranslate2.")
                return DeviceCapability(
                    device="cuda",
                    is_gpu=True,
                    device_name="CUDA Accelerator",
                    recommended_compute_type="int8_float16",
                    cpu_cores=cpu_cores
                )
        except Exception:
            pass

        # Check host nvidia-smi
        has_gpu, gpu_name, total_vram, free_vram = cls.detect_nvidia_smi()
        if has_gpu and gpu_name:
            compute_type = "float16" if (total_vram and total_vram >= 6000) else "int8_float16"
            logger.info("NVIDIA GPU detected via nvidia-smi: %s (%s MB VRAM)", gpu_name, total_vram)
            return DeviceCapability(
                device="cuda",
                is_gpu=True,
                device_name=gpu_name,
                vram_total_mb=total_vram,
                vram_free_mb=free_vram,
                recommended_compute_type=compute_type,
                cpu_cores=cpu_cores
            )

        # CPU Fallback
        logger.info("No active CUDA GPU detected. Falling back to CPU inference with int8 quantization.")
        return DeviceCapability(
            device="cpu",
            is_gpu=False,
            device_name="Host CPU (Quantized Fallback)",
            recommended_compute_type="int8",
            cpu_cores=cpu_cores
        )
