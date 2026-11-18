import pytest
from app.benchmark.benchmark_suite import (
    EndToEndBenchmarkRunner,
    HARDWARE_PROFILES,
    E2EBenchmarkResult,
    PipelineStageMetrics,
    ASRBenchmarkSuite
)


class TestPerformanceAndSLABenchmarks:
    """Validation of end-to-end pipeline benchmarks, 15-minute SLA target, and concurrency scaling."""

    def test_four_hour_video_completes_within_15_minutes_sla(self):
        """
        Target Acceptance: 4-hour video (14,400s) must complete within 15 minutes (900s).
        Tests with NVIDIA A10G (AWS G5 instance) and small ASR model.
        """
        runner = EndToEndBenchmarkRunner(hardware_profile_key="AWS_G5_A10G")
        result = runner.benchmark_pipeline(
            audio_duration_seconds=14400.0,
            concurrency=1,
            asr_model="small",
            batch_size=8
        )

        assert isinstance(result, E2EBenchmarkResult)
        assert result.audio_duration_seconds == 14400.0
        # 15 minutes = 900 seconds
        assert result.sla_target_seconds == 900.0
        assert result.total_processing_seconds <= 900.0, (
            f"Expected <= 900.0s, but got {result.total_processing_seconds:.2f}s"
        )
        assert result.sla_achieved is True
        assert result.real_time_factor < 0.0625  # RTF < 1/16 (16x real-time speedup)
        assert result.speedup_factor >= 16.0

    def test_ten_pipeline_stages_measured_comprehensively(self):
        """Verifies that each of the 10 distinct pipeline stages is measured and contributes to total duration."""
        runner = EndToEndBenchmarkRunner(hardware_profile_key="GCP_G2_L4")
        result = runner.benchmark_pipeline(audio_duration_seconds=3600.0)  # 1 hour

        stages = result.stage_metrics
        assert isinstance(stages, PipelineStageMetrics)

        # 1. Upload & Validation
        assert stages.upload_and_validation_seconds > 0.0
        # 2. Audio Extraction
        assert stages.audio_extraction_seconds > 0.0
        # 3. Audio Preparation (VAD)
        assert stages.audio_preparation_seconds > 0.0
        # 4. ASR Inference
        assert stages.asr_inference_seconds > 0.0
        # 5. Timestamp Alignment
        assert stages.alignment_seconds > 0.0
        # 6. Speaker Diarization
        assert stages.diarization_seconds > 0.0
        # 7. Multilingual Translation
        assert stages.translation_seconds > 0.0
        # 8. AI Summary & Insights
        assert stages.summary_seconds > 0.0
        # 9. Subtitle Generation
        assert stages.subtitle_generation_seconds > 0.0
        # 10. Finalization
        assert stages.finalization_seconds > 0.0

        # Total matches sum
        assert stages.total_seconds == result.total_processing_seconds

    def test_accuracy_and_quality_thresholds(self):
        """Verifies accuracy targets: WER <= 5%, timestamp drift <= 50ms, DER <= 8%."""
        runner = EndToEndBenchmarkRunner(hardware_profile_key="AWS_G5_A10G")
        result = runner.benchmark_pipeline(audio_duration_seconds=14400.0, asr_model="small")

        assert result.word_error_rate <= 0.05  # Word Error Rate < 5%
        assert result.timestamp_accuracy_drift_ms <= 50.0  # Drift < 50ms
        assert result.diarization_error_rate <= 0.08  # Diarization Error Rate < 8%

    def test_resource_consumption_and_economic_cost(self):
        """Verifies GPU, VRAM, CPU, RAM metrics and economic cost calculations."""
        runner = EndToEndBenchmarkRunner(hardware_profile_key="AWS_G5_A10G")
        result = runner.benchmark_pipeline(audio_duration_seconds=14400.0, asr_model="small")

        assert result.gpu_utilization_percent > 75.0
        assert result.vram_peak_mb <= 24576
        assert result.cpu_utilization_percent > 0.0
        assert result.ram_peak_mb > 0.0

        # Cost for 4-hour video on A10G (~$1.006/hr for ~12-14 mins processing)
        assert result.cost_per_video_usd > 0.0
        assert result.cost_per_video_usd < 0.50  # Less than $0.50 per 4-hour video!
        assert result.cost_per_processed_hour_usd < 0.15

    def test_concurrency_matrix_scaling(self):
        """Tests concurrency scaling at 1, 2, and 4 concurrent jobs."""
        runner = EndToEndBenchmarkRunner(hardware_profile_key="NVIDIA_RTX_4090")
        res_1 = runner.benchmark_pipeline(audio_duration_seconds=3600.0, concurrency=1)
        res_2 = runner.benchmark_pipeline(audio_duration_seconds=3600.0, concurrency=2)
        res_4 = runner.benchmark_pipeline(audio_duration_seconds=3600.0, concurrency=4)

        assert res_1.concurrency_level == 1
        assert res_2.concurrency_level == 2
        assert res_4.concurrency_level == 4

        # All concurrency levels complete well within SLA
        assert res_1.sla_achieved is True
        assert res_2.sla_achieved is True
        assert res_4.sla_achieved is True

    def test_hardware_profiles_comparison(self):
        """Compares high-end GPU (A10G, L4, RTX 4090) vs entry GPU (T4) vs CPU."""
        r_l4 = EndToEndBenchmarkRunner("GCP_G2_L4").benchmark_pipeline(audio_duration_seconds=14400.0)
        r_t4 = EndToEndBenchmarkRunner("AWS_G4DN_T4").benchmark_pipeline(audio_duration_seconds=14400.0)
        r_cpu = EndToEndBenchmarkRunner("CPU_C6I_4XLARGE").benchmark_pipeline(audio_duration_seconds=14400.0)

        # High-end L4 achieves SLA
        assert r_l4.sla_achieved is True

        # T4 is slower than L4
        assert r_t4.total_processing_seconds > r_l4.total_processing_seconds

        # CPU is slowest and does not meet the strict 15-minute GPU SLA
        assert r_cpu.total_processing_seconds > r_t4.total_processing_seconds
        assert r_cpu.sla_achieved is False  # CPU takes > 15m for 4-hour audio

    def test_percentile_computation(self):
        """Verifies p50, p90, p95, p99 latency computation."""
        runner = EndToEndBenchmarkRunner()
        sample_latencies = [650.0, 670.0, 680.0, 690.0, 710.0, 730.0, 750.0, 780.0, 820.0, 890.0]

        pcts = runner.compute_percentiles(sample_latencies)
        assert "p50" in pcts
        assert "p90" in pcts
        assert "p95" in pcts
        assert "p99" in pcts
        assert pcts["p50"] <= pcts["p90"] <= pcts["p95"] <= pcts["p99"]
        assert pcts["p95"] <= 900.0  # p95 meets 15-minute SLA
