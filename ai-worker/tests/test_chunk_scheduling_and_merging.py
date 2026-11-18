import pytest
from unittest.mock import MagicMock
from app.processors.chunk_scheduler import ChunkScheduler
from app.processors.transcript_merger import TranscriptMerger
from app.processors.vad_chunker import AudioChunk
from app.providers.base_provider import ASRProvider, TranscriptionOutput, TranscriptionSegment, WordTimestamp
from app.providers.device_detector import DeviceCapability


def test_chunk_scheduler_concurrency_cpu():
    mock_provider = MagicMock(spec=ASRProvider)
    cpu_cap = DeviceCapability(
        device="cpu",
        is_gpu=False,
        device_name="Intel Core i7",
        recommended_compute_type="int8",
        cpu_cores=8
    )
    scheduler = ChunkScheduler(provider=mock_provider, device_capability=cpu_cap)
    assert scheduler.concurrency == 1


def test_chunk_scheduler_concurrency_gpu_tiers():
    mock_provider = MagicMock(spec=ASRProvider)

    # 4GB GPU -> 1
    gpu_low = DeviceCapability(
        device="cuda",
        is_gpu=True,
        device_name="NVIDIA GTX 1650",
        recommended_compute_type="float16",
        cpu_cores=8,
        vram_total_mb=4096
    )
    assert ChunkScheduler(provider=mock_provider, device_capability=gpu_low).concurrency == 1

    # 8GB GPU -> 2
    gpu_mid = DeviceCapability(
        device="cuda",
        is_gpu=True,
        device_name="NVIDIA RTX 3070",
        recommended_compute_type="float16",
        cpu_cores=8,
        vram_total_mb=8192
    )
    assert ChunkScheduler(provider=mock_provider, device_capability=gpu_mid).concurrency == 2

    # 24GB GPU -> 4
    gpu_high = DeviceCapability(
        device="cuda",
        is_gpu=True,
        device_name="NVIDIA RTX 4090",
        recommended_compute_type="float16",
        cpu_cores=16,
        vram_total_mb=24576
    )
    assert ChunkScheduler(provider=mock_provider, device_capability=gpu_high).concurrency == 4


def test_chunk_scheduler_retry_success_after_failure():
    mock_provider = MagicMock(spec=ASRProvider)
    chunk = AudioChunk(chunk_index=0, start_time=0.0, end_time=10.0, file_path="c0.wav", duration=10.0)
    expected_output = TranscriptionOutput(
        text="hello world",
        segments=[],
        provider_name="mock-engine",
        model_name="base"
    )

    # Fail once, then succeed
    mock_provider.transcribe.side_effect = [RuntimeError("Transient GPU CUDA error"), expected_output]

    scheduler = ChunkScheduler(provider=mock_provider, max_chunk_retries=3)
    out = scheduler.process_chunk_with_retry(chunk)
    assert out == expected_output
    assert mock_provider.transcribe.call_count == 2


def test_chunk_scheduler_retry_exhaustion():
    mock_provider = MagicMock(spec=ASRProvider)
    chunk = AudioChunk(chunk_index=0, start_time=0.0, end_time=10.0, file_path="c0.wav", duration=10.0)
    mock_provider.transcribe.side_effect = RuntimeError("Persistent OOM")

    scheduler = ChunkScheduler(provider=mock_provider, max_chunk_retries=2)
    with pytest.raises(RuntimeError, match="failed permanently after 2 attempts"):
        scheduler.process_chunk_with_retry(chunk)


def test_chunk_scheduler_schedule_chunks():
    mock_provider = MagicMock(spec=ASRProvider)
    chunk1 = AudioChunk(chunk_index=0, start_time=0.0, end_time=10.0, file_path="c0.wav", duration=10.0)
    chunk2 = AudioChunk(chunk_index=1, start_time=9.0, end_time=20.0, file_path="c1.wav", duration=11.0, overlap_seconds=1.0)
    out1 = TranscriptionOutput(text="chunk 1 text", segments=[], provider_name="mock-engine", model_name="base")
    out2 = TranscriptionOutput(text="chunk 2 text", segments=[], provider_name="mock-engine", model_name="base")
    mock_provider.transcribe.side_effect = [out1, out2]

    scheduler = ChunkScheduler(provider=mock_provider)
    progress_callbacks = []

    def on_progress(idx, total, c):
        progress_callbacks.append((idx, total, c.chunk_index))

    results = scheduler.schedule_chunks([chunk1, chunk2], on_chunk_completed=on_progress)
    assert len(results) == 2
    assert progress_callbacks == [(1, 2, 0), (2, 2, 1)]


def test_transcript_merger_empty():
    assert TranscriptMerger.merge_chunks([]) == []


def test_transcript_merger_stitching_and_overlap_deduplication():
    # Chunk 0: 0.0s - 10.0s
    chunk0 = AudioChunk(chunk_index=0, start_time=0.0, end_time=10.0, file_path="c0.wav", duration=10.0, overlap_seconds=0.0)
    seg0_1 = TranscriptionSegment(
        id=0,
        start_time=0.5,
        end_time=4.5,
        text="Hello and welcome",
        words=[WordTimestamp(word="Hello", start_time=0.5, end_time=1.0, confidence=0.95)]
    )
    seg0_2 = TranscriptionSegment(
        id=1,
        start_time=5.0,
        end_time=9.5,
        text="to the video transcription engine.",
        words=[WordTimestamp(word="engine", start_time=8.5, end_time=9.5, confidence=0.98)]
    )
    out0 = TranscriptionOutput(
        text="Hello and welcome to the video transcription engine.",
        segments=[seg0_1, seg0_2],
        provider_name="mock-engine",
        model_name="base"
    )

    # Chunk 1: 9.0s - 20.0s (1.0s overlap: [9.0 - 10.0s])
    chunk1 = AudioChunk(chunk_index=1, start_time=9.0, end_time=20.0, file_path="c1.wav", duration=11.0, overlap_seconds=1.0)
    # Segment that falls completely in overlap window: [0.0 - 0.4s relative] -> [9.0 - 9.4s global] -> should be skipped!
    duplicate_seg = TranscriptionSegment(
        id=0,
        start_time=0.0,
        end_time=0.4,
        text="engine.",
        words=[WordTimestamp(word="engine.", start_time=0.0, end_time=0.4, confidence=0.97)]
    )
    # Segment in new audio: [1.2 - 6.0s relative] -> [10.2 - 15.0s global]
    seg1_1 = TranscriptionSegment(
        id=1,
        start_time=1.2,
        end_time=6.0,
        text="Now we continue with the next sentence.",
        words=[WordTimestamp(word="Now", start_time=1.2, end_time=1.8, confidence=0.93)]
    )
    out1 = TranscriptionOutput(
        text="engine. Now we continue with the next sentence.",
        segments=[duplicate_seg, seg1_1],
        provider_name="mock-engine",
        model_name="base"
    )

    merged = TranscriptMerger.merge_chunks([(chunk0, out0), (chunk1, out1)])

    # Should have 3 segments: seg0_1, seg0_2, and seg1_1 (duplicate_seg skipped)
    assert len(merged) == 3
    assert merged[0].id == 0
    assert merged[0].start_time == 0.5
    assert merged[0].end_time == 4.5
    assert merged[0].text == "Hello and welcome"

    assert merged[1].id == 1
    assert merged[1].start_time == 5.0
    assert merged[1].end_time == 9.5
    assert merged[1].text == "to the video transcription engine."

    assert merged[2].id == 2
    # Global time: 9.0 + 1.2 = 10.2s start, 9.0 + 6.0 = 15.0s end
    assert merged[2].start_time == 10.2
    assert merged[2].end_time == 15.0
    assert merged[2].text == "Now we continue with the next sentence."
    assert merged[2].words[0].start_time == 10.2
    assert merged[2].words[0].end_time == 10.8


def test_chunk_scheduler_parallel_execution():
    mock_provider = MagicMock(spec=ASRProvider)
    chunk0 = AudioChunk(chunk_index=0, start_time=0.0, end_time=10.0, file_path="c0.wav", duration=10.0)
    chunk1 = AudioChunk(chunk_index=1, start_time=10.0, end_time=20.0, file_path="c1.wav", duration=10.0)
    chunk2 = AudioChunk(chunk_index=2, start_time=20.0, end_time=30.0, file_path="c2.wav", duration=10.0)

    out0 = TranscriptionOutput(text="chunk 0", segments=[], provider_name="mock", model_name="base")
    out1 = TranscriptionOutput(text="chunk 1", segments=[], provider_name="mock", model_name="base")
    out2 = TranscriptionOutput(text="chunk 2", segments=[], provider_name="mock", model_name="base")
    mock_provider.transcribe.side_effect = [out0, out1, out2]

    gpu_high = DeviceCapability(
        device="cuda",
        is_gpu=True,
        device_name="NVIDIA A10G",
        recommended_compute_type="float16",
        cpu_cores=16,
        vram_total_mb=24576
    )
    scheduler = ChunkScheduler(provider=mock_provider, device_capability=gpu_high)
    assert scheduler.concurrency == 4

    completed = []
    def on_progress(idx, total, c):
        completed.append(c.chunk_index)

    results = scheduler.schedule_chunks([chunk0, chunk1, chunk2], on_chunk_completed=on_progress)

    # All 3 chunks processed and returned in exact chronological index order
    assert len(results) == 3
    assert results[0][0].chunk_index == 0
    assert results[1][0].chunk_index == 1
    assert results[2][0].chunk_index == 2
    assert set(completed) == {0, 1, 2}

