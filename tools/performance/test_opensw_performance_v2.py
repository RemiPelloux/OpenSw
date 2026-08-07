#!/usr/bin/env python3

import base64
import json
import shlex
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from opensw_performance_v2 import (
    CaptureError,
    LAB_INSTRUMENTATION,
    SCHEMA,
    compare_summaries,
    load_manifests,
    merge_surfaceflinger_latency,
    nearest_rank,
    parse_lab_result,
    parse_surfaceflinger_latency,
    render_perfetto_config,
    resolve_surface,
    run_lab_command,
    summarize_frametimes,
    validate_manifest,
)
from opensw_lab import canonical_replay_sha256, execute as execute_lab, parser as lab_parser


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

    @patch("opensw_performance_v2.adb")
    def test_surface_resolution_prefers_active_blast_layer(self, mock_adb):
        mock_adb.return_value = "\n".join(
            [
                "com.remipelloux.opensw.profile/MainActivity#1",
                "SurfaceView[com.remipelloux.opensw.profile/EmulationActivity]#2",
                "SurfaceView[com.remipelloux.opensw.profile/EmulationActivity](BLAST)#3",
            ]
        )

        self.assertEqual(
            "SurfaceView[com.remipelloux.opensw.profile/EmulationActivity](BLAST)#3",
            resolve_surface(None, "com.remipelloux.opensw.profile", None),
        )

    @patch("opensw_performance_v2.adb")
    def test_surface_resolution_rejects_stale_requested_layer(self, mock_adb):
        mock_adb.return_value = "SurfaceView[com.remipelloux.opensw.profile/Game](BLAST)#4"

        with self.assertRaises(CaptureError):
            resolve_surface(
                None,
                "com.remipelloux.opensw.profile",
                "SurfaceView[com.remipelloux.opensw.profile/Game](BLAST)#3",
            )

    def test_perfetto_config_preserves_literal_braces(self):
        config = render_perfetto_config("com.remipelloux.opensw.profile", 60_000)
        self.assertIn("buffers: { size_kb: 65536", config)
        self.assertIn('atrace_apps: "com.remipelloux.opensw.profile"', config)
        self.assertIn("duration_ms: 60000", config)
        self.assertNotIn("{package}", config)

    def test_lab_result_decodes_instrumentation_payload(self):
        payload = base64.b64encode(json.dumps({"ok": True, "value": {"pid": 42}}).encode()).decode()
        result = parse_lab_result(f"INSTRUMENTATION_RESULT: stream=OPEN_SW_LAB_RESULT={payload}\n")
        self.assertEqual(42, result["value"]["pid"])

    def test_lab_result_rejects_non_object_payload(self):
        payload = base64.b64encode(json.dumps(["unexpected"]).encode()).decode()
        with self.assertRaises(CaptureError):
            parse_lab_result(f"OPEN_SW_LAB_RESULT={payload}\n")

    @patch("opensw_performance_v2.adb")
    def test_lab_command_shell_quotes_argument_values(self, mock_adb):
        payload = base64.b64encode(json.dumps({"ok": True}).encode()).decode()
        mock_adb.return_value = f"OPEN_SW_LAB_RESULT={payload}\n"

        run_lab_command(
            "device",
            "set-cheat",
            {"name": "60 FPS (WARNING)", "enabled": "true"},
        )

        remote_command = mock_adb.call_args.args[2]
        self.assertEqual("shell", mock_adb.call_args.args[1])
        self.assertEqual(
            [
                "am",
                "instrument",
                "-w",
                "-r",
                "-e",
                "command",
                "set-cheat",
                "-e",
                "name",
                "60 FPS (WARNING)",
                "-e",
                "enabled",
                "true",
                LAB_INSTRUMENTATION,
            ],
            shlex.split(remote_command),
        )

    def test_replay_hash_is_canonical(self):
        replay = {
            "schema": "opensw-input-replay-v1",
            "title_id": "01001f5010dfa000",
            "game_version": "1.1.1",
            "controller_id": "A" * 32,
            "controller_port": 0,
            "duration_ns": 1_000,
            "events": [
                {"timestamp_ns": 0, "kind": "BUTTON", "control": 96, "value": 1.0}
            ],
        }
        digest = canonical_replay_sha256(replay)
        self.assertEqual(
            "9a09a980fa6153263eb841ff5f5acfcf36eefc12525ceebed23e43b2b1675f6f",
            digest,
        )
        replay["sha256"] = "ignored"
        self.assertEqual(digest, canonical_replay_sha256(replay))

    def test_lab_parser_accepts_native_capture_commands(self):
        start = lab_parser().parse_args(
            ["start-capture", "--title-id", "01001F5010DFA000", "--mode", "STANDARD"]
        )
        finish = lab_parser().parse_args(
            ["finish-capture", "--output", "capture.json"]
        )
        self.assertEqual("start-capture", start.command)
        self.assertEqual("finish-capture", finish.command)

        enable = lab_parser().parse_args(["enable-cheat", "60 FPS"])
        disable = lab_parser().parse_args(["disable-cheat", "60 FPS"])
        self.assertEqual("enable-cheat", enable.command)
        self.assertEqual("disable-cheat", disable.command)

        graphics = lab_parser().parse_args(
            [
                "set-graphics",
                "--resolution",
                "0.75x",
                "--scaling-filter",
                "fsr",
                "--sharpening",
                "20",
            ]
        )
        self.assertEqual("0.75x", graphics.resolution)
        self.assertEqual("fsr", graphics.scaling_filter)
        self.assertEqual(20, graphics.sharpening)

    @patch("opensw_lab.run_lab_command")
    def test_lab_graphics_command_maps_symbolic_values(self, mock_run_lab_command):
        mock_run_lab_command.return_value = {"ok": True}
        graphics = lab_parser().parse_args(
            [
                "--serial",
                "device",
                "set-graphics",
                "--resolution",
                "0.75x",
                "--scaling-filter",
                "fsr",
                "--sharpening",
                "20",
            ]
        )

        self.assertEqual(0, execute_lab(graphics))
        mock_run_lab_command.assert_called_once_with(
            "device",
            "set-graphics",
            {"resolution": "2", "scaling_filter": "6", "sharpening": "20"},
        )

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
        manifest = self.manifest()
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

    def test_revision_two_requires_runtime_and_matching_apk(self):
        manifest = self.manifest()
        manifest["runtime_identity"] = None
        with self.assertRaises(CaptureError):
            validate_manifest(manifest)

        manifest = self.manifest()
        manifest["installed_apk"]["sha256"] = "other"
        with self.assertRaises(CaptureError):
            validate_manifest(manifest)

    def test_summary_rejects_replay_mismatch_and_non_finite_metric(self):
        manifest = self.manifest()
        manifest["runtime_identity"]["replay_sha256"] = "other"
        with self.assertRaises(CaptureError):
            validate_manifest(manifest)

        manifest = self.manifest()
        manifest["summary"]["median_fps"] = float("nan")
        with self.assertRaises(CaptureError):
            validate_manifest(manifest)

    def test_comparison_rejects_stable_control_drift(self):
        baseline = self.summary("a", fps=30.0, p95=40.0, p99=60.0)
        candidate = self.summary("b", fps=31.0, p95=39.0, p99=58.0)
        candidate["configuration"]["gpu_driver"] = "different"
        with self.assertRaises(CaptureError):
            compare_summaries(baseline, candidate)

    def test_comparison_rejects_multiple_experiment_dimensions(self):
        baseline = self.summary("a", fps=30.0, p95=40.0, p99=60.0)
        candidate = self.summary("b", fps=31.0, p95=39.0, p99=58.0)
        candidate["configuration"]["runtime_identity.requested_workers"] = 6
        candidate["configuration"]["local_apk_sha256"] = "different"
        with self.assertRaises(CaptureError):
            compare_summaries(baseline, candidate)

    @staticmethod
    def manifest():
        replay = "a" * 64
        runtime = {
            "schema": "opensw-runtime-identity-v1",
            "pid": 42,
            "package_version": "test",
            "session_generation": 7,
            "session_state": "RUNNING",
            "surface_attached": True,
            "title_id": "01001f5010dfa000",
            "requested_workers": 4,
            "effective_workers": 4,
            "worker_reason": "EXPLICIT",
            "async_gpu": True,
            "async_shaders": True,
            "async_presentation": True,
            "descriptor_buffer_available": True,
            "presentation_target": 60,
            "replay_sha256": replay,
            "replay_state": "COMPLETED",
            "monotonic_timestamp_ms": 1000,
        }
        return {
            "schema": SCHEMA,
            "manifest_revision": 2,
            "title_id": "01001f5010dfa000",
            "scenario_sha256": "scenario",
            "variant": "baseline",
            "experiment_key": "workers",
            "package": "com.remipelloux.opensw.profile",
            "local_apk": {"sha256": "apk"},
            "installed_apk": {"sha256": "apk", "version": "test"},
            "git": {"head": "head", "source_tree_sha256": "tree"},
            "switch_firmware": "firmware",
            "game_version": "1.1.1",
            "profile": "handheld",
            "cache_state": "cold",
            "cheat_set_sha256": "b" * 64,
            "fan_mode": "sport",
            "initial_temperature_c": 35.0,
            "temperature_band_c": [34.0, 36.0],
            "replay_sha256": replay,
            "runtime_identity": runtime,
            "summary": {
                "median_fps": 30.0,
                "frametime_p50_ms": 33.0,
                "frametime_p95_ms": 40.0,
                "frametime_p99_ms": 60.0,
                "frame_count": 1800,
                "rss_max_kib": 1000,
                "temperature_max_c": 40.0,
            },
            "device": {
                "manufacturer": "AYN",
                "model": "Thor",
                "build_fingerprint": "fingerprint",
                "android_release": "13",
                "resolution": "1920x1080",
                "gpu_driver": "driver",
            },
        }

    @staticmethod
    def summary(variant, fps, p95, p99):
        return {
            "schema": SCHEMA,
            "manifest_revision": 2,
            "run_count": 5,
            "title_id": "01001f5010dfa000",
            "scenario_sha256": "scenario",
            "variant": variant,
            "experiment_key": "workers",
            "configuration": {
                "gpu_driver": "driver",
                "local_apk_sha256": "apk",
                "runtime_identity.requested_workers": 4 if variant == "a" else 6,
                "runtime_identity.effective_workers": 4 if variant == "a" else 6,
                "runtime_identity.worker_reason": "EXPLICIT",
            },
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
