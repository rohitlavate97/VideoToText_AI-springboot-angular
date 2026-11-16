from app.benchmark.benchmark_suite import ASRBenchmarkSuite, BenchmarkMetric, BenchmarkReport
from app.providers.mock_provider import MockASRProvider


def test_benchmark_suite_single_duration():
    provider = MockASRProvider()
    suite = ASRBenchmarkSuite(provider=provider, max_chunk_duration=600)

    metric = suite.benchmark_duration("10-minutes", 600)

    assert isinstance(metric, BenchmarkMetric)
    assert metric.target_name == "10-minutes"
    assert metric.audio_duration_seconds == 600.0
    assert metric.real_time_factor > 0.0
    assert metric.real_time_factor < 1.0  # Faster than real-time
    assert metric.chunks_processed == 1
    assert metric.segments_generated > 0
    assert metric.transcription_completeness_percent == 100.0
    assert metric.estimated_word_error_rate < 0.05


def test_benchmark_suite_full_run():
    provider = MockASRProvider()
    suite = ASRBenchmarkSuite(provider=provider, max_chunk_duration=600)

    report = suite.run_full_suite()

    assert isinstance(report, BenchmarkReport)
    assert len(report.metrics) == 4  # 10m, 30m, 1h, 4h
    assert report.average_rtf > 0.0
    assert report.average_rtf < 1.0

    targets = [m.target_name for m in report.metrics]
    assert "10-minutes" in targets
    assert "30-minutes" in targets
    assert "1-hour" in targets
    assert "4-hours" in targets

    # 4-hour video must have 24 chunks (14400s / 600s = 24)
    four_hour_metric = next(m for m in report.metrics if m.target_name == "4-hours")
    assert four_hour_metric.chunks_processed == 24
    assert four_hour_metric.audio_duration_seconds == 14400.0
    assert four_hour_metric.transcription_completeness_percent == 100.0
