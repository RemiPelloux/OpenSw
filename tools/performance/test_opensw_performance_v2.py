#!/usr/bin/env python3

import json
import tempfile
import unittest
from pathlib import Path

from opensw_performance_v2 import (
    CaptureError,
    SCHEMA,
    compare_summaries,
    load_manifests,
    merge_surfaceflinger_latency,
    nearest_rank,
    parse_surfaceflinger_latency,
    summarize_frametimes,
)


class PerformanceV2Test(unittest.TestCase):
    def test_nearest_rank(self):
        values = list(range(1, 101))
        self.assertEqual(50, nearest_rank(values, 0.50))
        self.assertEqual(95, nearest_rank(values, 0.95))
        self.assertEqual(99, nearest_rank(values, 0.99))

    def test_surfaceflinger_uses_actual_present_timestamps(self):
        raw = "16666666\n1 10000000 3\n4 26000000 6\n7 45000000 9\n"
        refresh, timestamps, frametimes = parse_surfaceflinger_latency(raw)
        self.assertEqual(16666666, refresh)
        self.assertEqual([10000000, 26000000, 45000000], timestamps)
        self.assertEqual([16.0, 19.0], frametimes)

    def test_empty_summary_is_finite(self):
        summary = summarize_frametimes([])
        self.assertEqual(0, summary["frame_count"])
        self.assertEqual(0.0, summary["median_fps"])
        self.assertEqual(0.0, summary["frametime_p99_ms"])

    def test_surfaceflinger_samples_cover_the_capture_window(self):
        first = "16666666\n1 10000000 3\n4 26000000 6\n7 45000000 9\n"
        second = "16666666\n7 45000000 9\n10 65000000 12\n"
        refresh, timestamps, frametimes = merge_surfaceflinger_latency([first, second])
        self.assertEqual(16666666, refresh)
        self.assertEqual([10000000, 26000000, 45000000, 65000000], timestamps)
        self.assertEqual([16.0, 19.0, 20.0], frametimes)

    def test_comparison_promotes_tail_improvement_without_regression(self):
        baseline = self.summary("a", fps=30.0, p95=40.0, p99=60.0)
        candidate = self.summary("b", fps=30.2, p95=39.0, p99=58.0)
        result = compare_summaries(baseline, candidate)
        self.assertEqual("promote", result["verdict"])

    def test_comparison_rejects_regression_over_two_percent(self):
        baseline = self.summary("a", fps=30.0, p95=40.0, p99=60.0)
        candidate = self.summary("b", fps=31.0, p95=41.0, p99=60.0)
        result = compare_summaries(baseline, candidate)
        self.assertEqual("reject", result["verdict"])
        self.assertEqual("regression_over_2_percent", result["reason"])

    def test_comparison_rejects_memory_regression(self):
        baseline = self.summary("a", fps=30.0, p95=40.0, p99=60.0)
        candidate = self.summary("b", fps=31.0, p95=39.0, p99=59.0)
        candidate["median_of_runs"]["rss_max_kib"] = 1100.0
        result = compare_summaries(baseline, candidate)
        self.assertEqual("reject", result["verdict"])
        self.assertEqual("regression_over_2_percent", result["reason"])

    def test_summary_rejects_mixed_device_configuration(self):
        manifest = {
            "schema": SCHEMA,
            "title_id": "01001f5010dfa000",
            "scenario_sha256": "scenario",
            "variant": "baseline",
            "package": "com.remipelloux.opensw.profile",
            "local_apk": {"sha256": "local"},
            "installed_apk": {"sha256": "installed", "version": "1"},
            "git": {"head": "head", "source_tree_sha256": "tree"},
            "switch_firmware": "firmware",
            "profile": "handheld",
            "device": {
                "manufacturer": "AYN",
                "model": "Thor",
                "build_fingerprint": "fingerprint",
                "android_release": "13",
                "resolution": "1920x1080",
                "gpu_driver": "driver",
            },
        }
        with tempfile.TemporaryDirectory() as directory:
            paths = []
            for index in range(5):
                current = json.loads(json.dumps(manifest))
                if index == 4:
                    current["device"]["resolution"] = "1080x1920"
                path = Path(directory) / f"run-{index}.json"
                path.write_text(json.dumps(current), encoding="utf-8")
                paths.append(str(path))
            with self.assertRaises(CaptureError):
                load_manifests(paths)

    @staticmethod
    def summary(variant, fps, p95, p99):
        return {
            "schema": SCHEMA,
            "run_count": 5,
            "title_id": "01001f5010dfa000",
            "scenario_sha256": "scenario",
            "variant": variant,
            "median_of_runs": {
                "median_fps": fps,
                "frametime_p50_ms": 33.0,
                "frametime_p95_ms": p95,
                "frametime_p99_ms": p99,
                "frame_count": 1800.0,
                "rss_max_kib": 1000.0,
                "temperature_max_c": 50.0,
            },
        }


if __name__ == "__main__":
    unittest.main()
