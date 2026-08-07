#!/usr/bin/env python3
"""Reproducible OpenSw Android performance capture and A/B comparison."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import math
import os
import platform
import re
import shlex
import shutil
import statistics
import subprocess
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable, Sequence

SCHEMA = "opensw-performance-v2"
MANIFEST_REVISION = 2
RUNTIME_SCHEMA = "opensw-runtime-identity-v1"
ALLOWED_PACKAGES = {"com.remipelloux.opensw", "com.remipelloux.opensw.profile"}
REMOTE_TRACE = "/data/misc/perfetto-traces/opensw-performance-v2.pftrace"
TITLE_ID_MARKER = re.compile(r"OpenSw performance active title_id=([0-9a-fA-F]{16})")
LAB_RESULT_MARKER = "OPEN_SW_LAB_RESULT="
LAB_INSTRUMENTATION = (
    "com.remipelloux.opensw.lab.test/com.remipelloux.opensw.lab.OpenSwLabRunner"
)

PERFETTO_CONFIG = """
buffers: { size_kb: 65536 fill_policy: RING_BUFFER }
data_sources: {
  config {
    name: "linux.ftrace"
    ftrace_config {
      ftrace_events: "sched/sched_switch"
      ftrace_events: "sched/sched_wakeup"
      ftrace_events: "power/cpu_frequency"
      ftrace_events: "power/cpu_idle"
      ftrace_events: "binder/binder_transaction"
      atrace_categories: "gfx"
      atrace_categories: "view"
      atrace_categories: "sched"
      atrace_categories: "freq"
      atrace_categories: "idle"
      atrace_categories: "binder_driver"
      atrace_categories: "hal"
      atrace_categories: "am"
      atrace_categories: "wm"
      atrace_categories: "surfaceflinger"
      atrace_apps: "{package}"
    }
  }
}
data_sources: { config { name: "linux.process_stats" } }
duration_ms: {duration_ms}
write_into_file: true
file_write_period_ms: 2500
""".strip()


def render_perfetto_config(package: str, duration_ms: int) -> str:
    return PERFETTO_CONFIG.replace("{package}", package).replace(
        "{duration_ms}", str(duration_ms)
    )


class CaptureError(RuntimeError):
    pass


REQUIRED_RUNTIME_FIELDS = {
    "pid",
    "package_version",
    "session_generation",
    "session_state",
    "surface_attached",
    "title_id",
    "requested_workers",
    "effective_workers",
    "worker_reason",
    "async_gpu",
    "async_shaders",
    "async_presentation",
    "descriptor_buffer_available",
    "presentation_target",
    "replay_sha256",
    "replay_state",
    "monotonic_timestamp_ms",
}

EXPERIMENT_FIELDS = {
    "workers": {
        "runtime_identity.requested_workers",
        "runtime_identity.effective_workers",
        "runtime_identity.worker_reason",
    },
    "build": {
        "local_apk_sha256",
        "installed_apk_sha256",
        "installed_apk_version",
        "runtime_identity.package_version",
        "git_head",
        "source_tree_sha256",
    },
}


def run(command: Sequence[str], *, input_text: str | None = None, check: bool = True) -> str:
    result = subprocess.run(
        command,
        input=input_text,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if check and result.returncode != 0:
        raise CaptureError(f"Command failed ({result.returncode}): {' '.join(command)}\n{result.stderr}")
    return result.stdout.strip()


def adb(serial: str | None, *arguments: str, check: bool = True) -> str:
    command = ["adb"]
    if serial:
        command += ["-s", serial]
    command.extend(arguments)
    return run(command, check=check)


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def source_tree_digest(root: Path) -> str:
    def git_paths(*arguments: str) -> list[bytes]:
        result = subprocess.run(
            ["git", "ls-files", "-z", *arguments],
            cwd=root,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        if result.returncode != 0:
            raise CaptureError(result.stderr.decode(errors="replace").strip())
        return [path for path in result.stdout.split(b"\0") if path]

    tracked_paths = git_paths("--cached")
    untracked_paths = [
        path
        for path in git_paths("--others", "--exclude-standard")
        if os.fsdecode(path).startswith(("src/", "tools/"))
    ]
    digest = hashlib.sha256()
    for raw_path in sorted(set(tracked_paths + untracked_paths)):
        relative_path = os.fsdecode(raw_path)
        path = root / relative_path
        digest.update(raw_path)
        digest.update(b"\0")
        if path.is_symlink():
            digest.update(os.readlink(path).encode())
        elif path.is_file():
            with path.open("rb") as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                    digest.update(chunk)
        else:
            digest.update(b"<missing>")
        digest.update(b"\0")
    return digest.hexdigest()


def nearest_rank(values: Sequence[float], percentile: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    rank = max(1, math.ceil(percentile * len(ordered)))
    return ordered[rank - 1]


def parse_surfaceflinger_latency(raw: str) -> tuple[int, list[int], list[float]]:
    lines = [line.strip() for line in raw.splitlines() if line.strip()]
    if not lines:
        return 0, [], []
    try:
        refresh_period_ns = int(lines[0])
    except ValueError as error:
        raise CaptureError("Invalid SurfaceFlinger latency header") from error

    present_timestamps: list[int] = []
    for line in lines[1:]:
        columns = line.split()
        if len(columns) < 3:
            continue
        try:
            actual_present_ns = int(columns[1])
        except ValueError:
            continue
        if actual_present_ns <= 0 or actual_present_ns == (1 << 63) - 1:
            continue
        if not present_timestamps or actual_present_ns > present_timestamps[-1]:
            present_timestamps.append(actual_present_ns)

    frametimes_ms = [
        (current - previous) / 1_000_000.0
        for previous, current in zip(present_timestamps, present_timestamps[1:])
        if current > previous
    ]
    return refresh_period_ns, present_timestamps, frametimes_ms


def merge_surfaceflinger_latency(samples: Sequence[str]) -> tuple[int, list[int], list[float]]:
    refresh_period_ns = 0
    present_timestamps: set[int] = set()
    for raw in samples:
        sample_refresh_period, sample_timestamps, _ = parse_surfaceflinger_latency(raw)
        if sample_refresh_period > 0:
            refresh_period_ns = sample_refresh_period
        present_timestamps.update(sample_timestamps)
    ordered_timestamps = sorted(present_timestamps)
    frametimes_ms = [
        (current - previous) / 1_000_000.0
        for previous, current in zip(ordered_timestamps, ordered_timestamps[1:])
        if current > previous
    ]
    return refresh_period_ns, ordered_timestamps, frametimes_ms


def summarize_frametimes(frametimes_ms: Sequence[float]) -> dict[str, float | int]:
    finite = [value for value in frametimes_ms if math.isfinite(value) and value > 0]
    fps = [1000.0 / value for value in finite]
    return {
        "frame_count": len(finite),
        "frametime_p50_ms": nearest_rank(finite, 0.50),
        "frametime_p95_ms": nearest_rank(finite, 0.95),
        "frametime_p99_ms": nearest_rank(finite, 0.99),
        "median_fps": statistics.median(fps) if fps else 0.0,
    }


def parse_meminfo_rss_kib(raw: str) -> int:
    for line in raw.splitlines():
        if line.strip().startswith("TOTAL RSS:"):
            parts = line.split()
            return int(parts[2])
    for line in raw.splitlines():
        if line.strip().startswith("TOTAL"):
            parts = line.split()
            if len(parts) >= 2 and parts[1].isdigit():
                return int(parts[1])
    return 0


def parse_temperatures_c(raw: str) -> list[float]:
    result: list[float] = []
    for token in raw.split():
        try:
            value = float(token)
        except ValueError:
            continue
        if value > 1000:
            value /= 1000.0
        if -20.0 <= value <= 150.0:
            result.append(value)
    return result


def git_manifest(root: Path) -> dict[str, Any]:
    head_result = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if head_result.returncode != 0:
        raise CaptureError(head_result.stderr.strip())
    head = head_result.stdout.strip()
    diff = subprocess.run(
        ["git", "diff", "--binary", "HEAD"], cwd=root, stdout=subprocess.PIPE, check=True
    ).stdout
    status = subprocess.run(
        ["git", "status", "--short"],
        cwd=root,
        text=True,
        stdout=subprocess.PIPE,
        check=True,
    ).stdout.strip()
    return {
        "head": head,
        "diff_sha256": sha256_bytes(diff),
        "source_tree_sha256": source_tree_digest(root),
        "dirty": bool(status),
        "status": status.splitlines(),
    }


def scenario_digest(scenario: dict[str, Any]) -> str:
    payload = json.dumps(scenario, sort_keys=True, separators=(",", ":")).encode()
    return sha256_bytes(payload)


def validate_runtime_identity(
    identity: dict[str, Any], *, pid: str, title_id: str, replay_sha256: str
) -> dict[str, Any]:
    if not isinstance(identity, dict) or identity.get("schema") != RUNTIME_SCHEMA:
        raise CaptureError(f"Runtime identity must use schema {RUNTIME_SCHEMA}")
    missing = sorted(REQUIRED_RUNTIME_FIELDS - identity.keys())
    if missing:
        raise CaptureError("Runtime identity is incomplete: " + ", ".join(missing))
    if str(identity["pid"]) != pid or int(identity["session_generation"]) <= 0:
        raise CaptureError("Runtime identity PID or generation is stale")
    if str(identity["title_id"]).lower() != title_id:
        raise CaptureError("Runtime identity Title ID does not match the capture")
    if str(identity["replay_sha256"]).lower() != replay_sha256.lower():
        raise CaptureError("Runtime identity replay does not match the declared replay")
    if identity["replay_state"] != "RUNNING":
        raise CaptureError("Runtime identity replay must be RUNNING at capture start")
    if identity["session_state"] != "RUNNING" or not identity["surface_attached"]:
        raise CaptureError("Runtime identity does not describe an active rendered session")
    if int(identity["effective_workers"]) < 1:
        raise CaptureError("Runtime identity reports an invalid effective worker count")
    return identity


def load_runtime_identity(path: Path, *, pid: str, title_id: str, replay_sha256: str) -> dict[str, Any]:
    identity = json.loads(path.read_text(encoding="utf-8"))
    return validate_runtime_identity(
        identity, pid=pid, title_id=title_id, replay_sha256=replay_sha256
    )


def parse_lab_result(output: str) -> dict[str, Any]:
    marker_index = output.find(LAB_RESULT_MARKER)
    if marker_index < 0:
        raise CaptureError("OpenSw Lab instrumentation did not return a result")
    encoded = output[marker_index + len(LAB_RESULT_MARKER) :].splitlines()[0].strip()
    try:
        result = json.loads(base64.b64decode(encoded, validate=True))
    except (ValueError, json.JSONDecodeError) as error:
        raise CaptureError("OpenSw Lab returned an invalid result") from error
    if not isinstance(result, dict):
        raise CaptureError("OpenSw Lab returned an invalid result")
    if not result.get("ok"):
        raise CaptureError(f"OpenSw Lab command failed: {result.get('error', 'rejected')}")
    return result


def run_lab_command(
    serial: str | None, command: str, arguments: dict[str, str] | None = None
) -> dict[str, Any]:
    instrument_arguments = ["-e", "command", command]
    for name, value in (arguments or {}).items():
        instrument_arguments += ["-e", name, value]
    remote_command = shlex.join(
        ["am", "instrument", "-w", "-r", *instrument_arguments, LAB_INSTRUMENTATION]
    )
    output = adb(serial, "shell", remote_command)
    return parse_lab_result(output)


def fetch_runtime_identity(
    serial: str | None, *, pid: str, title_id: str, replay_sha256: str
) -> dict[str, Any]:
    result = run_lab_command(serial, "runtime-identity")
    identity = result.get("value")
    if not isinstance(identity, dict):
        raise CaptureError("OpenSw Lab runtime identity is missing")
    return validate_runtime_identity(
        identity, pid=pid, title_id=title_id, replay_sha256=replay_sha256
    )


def resolve_surface(serial: str | None, package: str, requested: str | None) -> str:
    layers = adb(serial, "shell", "dumpsys", "SurfaceFlinger", "--list")
    matches = [line.strip() for line in layers.splitlines() if package in line]
    if requested:
        if package not in requested:
            raise CaptureError("The selected SurfaceFlinger layer does not belong to OpenSw")
        if requested not in matches:
            raise CaptureError("The selected SurfaceFlinger layer is not currently active")
        return requested
    blast_surfaces = [
        layer
        for layer in matches
        if layer.startswith("SurfaceView[") and "EmulationActivity](BLAST)#" in layer
    ]
    if len(blast_surfaces) == 1:
        return blast_surfaces[0]
    if len(matches) != 1:
        raise CaptureError(
            f"Expected one active OpenSw SurfaceFlinger layer, found {len(matches)}; use --surface"
        )
    return matches[0]


def installed_apk_manifest(serial: str | None, package: str) -> dict[str, str]:
    paths = adb(serial, "shell", "pm", "path", package)
    base_paths = [line.removeprefix("package:") for line in paths.splitlines() if line]
    digest = ""
    if base_paths:
        digest_output = adb(serial, "shell", "sha256sum", base_paths[0], check=False)
        if digest_output:
            digest = digest_output.split()[0]
    version = adb(serial, "shell", "dumpsys", "package", package)
    version_line = next(
        (line.strip() for line in version.splitlines() if line.strip().startswith("versionName=")), ""
    )
    return {
        "path": base_paths[0] if base_paths else "",
        "sha256": digest,
        "version": version_line.removeprefix("versionName="),
    }


def verify_active_title_id(serial: str | None, package: str, pid: str, expected: str) -> None:
    current_pid = adb(serial, "shell", "pidof", package, check=False).split()
    if current_pid != [pid]:
        raise CaptureError("The OpenSw process changed during capture")
    logs = adb(serial, "logcat", "-d", "--pid", pid, "-v", "raw", check=False)
    markers = TITLE_ID_MARKER.findall(logs)
    if not markers:
        raise CaptureError("OpenSw did not publish an active Title ID marker")
    active = markers[-1].lower()
    if active != expected:
        raise CaptureError(f"Active OpenSw Title ID changed from {expected} to {active}")


def parse_named_temperatures_c(raw: str) -> dict[str, float]:
    temperatures: dict[str, float] = {}
    for line in raw.splitlines():
        name, separator, raw_value = line.partition("=")
        if not separator:
            continue
        values = parse_temperatures_c(raw_value)
        if values:
            temperatures[name.strip()] = values[0]
    return temperatures


def parse_kgsl_sample(raw: str) -> dict[str, int | float]:
    values: dict[str, int | float] = {}
    for line in raw.splitlines():
        name, separator, raw_value = line.partition("=")
        if not separator:
            continue
        numbers = re.findall(r"\d+", raw_value)
        if name == "busy" and len(numbers) >= 2:
            values["busy_us"] = int(numbers[0])
            values["total_us"] = int(numbers[1])
        elif name == "util" and numbers:
            values["utilization_percent"] = int(numbers[0])
        elif name == "frequency" and numbers:
            values["frequency_hz"] = int(numbers[0])
        elif name == "temperature" and numbers:
            value = float(numbers[0])
            values["temperature_c"] = value / 1000.0 if value > 1000 else value
    return values


def parse_thermal_status(raw: str) -> int | None:
    match = re.search(r"Thermal Status:\s*(\d+)", raw)
    return int(match.group(1)) if match else None


def validate_perfetto_trace(path: Path, package: str) -> tuple[bool, str | None]:
    if path.stat().st_size < 4096:
        return False, "trace_too_small"
    if package.encode() not in path.read_bytes():
        return False, "missing_opensw_process_data"
    return True, None


def collect_sample(
    serial: str | None, package: str
) -> tuple[int, dict[str, float], dict[str, Any], int | None]:
    meminfo = adb(serial, "shell", "dumpsys", "meminfo", package, check=False)
    thermal = adb(
        serial,
        "shell",
        "for z in /sys/class/thermal/thermal_zone*; do "
        "n=$(cat \"$z/type\" 2>/dev/null); t=$(cat \"$z/temp\" 2>/dev/null); "
        "[ -n \"$n\" ] && [ -n \"$t\" ] && printf '%s=%s\\n' \"$n\" \"$t\"; done",
        check=False,
    )
    kgsl = adb(
        serial,
        "shell",
        "printf 'util='; cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>/dev/null; "
        "printf 'frequency='; cat /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null; "
        "printf 'busy='; cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null; "
        "printf 'temperature='; cat /sys/class/kgsl/kgsl-3d0/temp 2>/dev/null",
        check=False,
    )
    status = parse_thermal_status(
        adb(serial, "shell", "dumpsys", "thermalservice", check=False)
    )
    return (
        parse_meminfo_rss_kib(meminfo),
        parse_named_temperatures_c(thermal),
        parse_kgsl_sample(kgsl),
        status,
    )


def capture(args: argparse.Namespace) -> int:
    if args.package not in ALLOWED_PACKAGES:
        raise CaptureError(f"Unsupported package {args.package!r}")
    if shutil.which("adb") is None:
        raise CaptureError("adb is not available")
    if not 0.1 <= args.sample_interval <= 1.0:
        raise CaptureError("--sample-interval must be between 0.1 and 1.0 seconds")

    root = Path(args.repo).resolve()
    output = Path(args.output).resolve()
    output.mkdir(parents=True, exist_ok=False)
    scenario_path = Path(args.scenario)
    scenario = json.loads(scenario_path.read_text(encoding="utf-8"))
    if not isinstance(scenario, dict):
        raise CaptureError("Scenario manifest must be a JSON object")

    adb(args.serial, "get-state")
    expected_title_id = args.title_id.lower()
    if not re.fullmatch(r"[0-9a-f]{16}", expected_title_id):
        raise CaptureError("--title-id must contain exactly 16 hexadecimal characters")
    running_pids = adb(args.serial, "shell", "pidof", args.package, check=False).split()
    if len(running_pids) != 1:
        raise CaptureError("OpenSw must already be running before capture")
    pid = running_pids[0]
    apk = Path(args.apk).resolve()
    installed_apk = installed_apk_manifest(args.serial, args.package)
    local_apk_sha256 = sha256_file(apk)
    if not installed_apk.get("sha256") or installed_apk["sha256"] != local_apk_sha256:
        raise CaptureError("Local and installed APK hashes must match before capture")
    runtime_identity = (
        load_runtime_identity(
            Path(args.runtime_identity),
            pid=pid,
            title_id=expected_title_id,
            replay_sha256=args.replay_sha256,
        )
        if args.runtime_identity
        else fetch_runtime_identity(
            args.serial,
            pid=pid,
            title_id=expected_title_id,
            replay_sha256=args.replay_sha256,
        )
    )
    verify_active_title_id(args.serial, args.package, pid, expected_title_id)
    surface = resolve_surface(args.serial, args.package, args.surface)
    surface_arg = shlex.quote(surface)
    adb(args.serial, "shell", "dumpsys", "SurfaceFlinger", "--latency-clear", surface_arg)

    config = render_perfetto_config(args.package, args.duration * 1000)
    adb_command = ["adb"]
    if args.serial:
        adb_command += ["-s", args.serial]
    adb_command += ["shell", "perfetto", "-c", "-", "--txt", "-o", REMOTE_TRACE]
    perfetto = subprocess.Popen(
        adb_command,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    assert perfetto.stdin is not None
    perfetto.stdin.write(config)
    perfetto.stdin.close()

    rss_samples: list[int] = []
    temperature_samples: list[dict[str, float]] = []
    kgsl_samples: list[dict[str, Any]] = []
    thermal_status_samples: list[int] = []
    latency_samples: list[str] = []
    deadline = time.monotonic() + args.duration
    while time.monotonic() < deadline:
        verify_active_title_id(args.serial, args.package, pid, expected_title_id)
        rss, temperatures, kgsl, thermal_status = collect_sample(args.serial, args.package)
        if rss:
            rss_samples.append(rss)
        temperature_samples.append(temperatures)
        kgsl_samples.append(kgsl)
        if thermal_status is not None:
            thermal_status_samples.append(thermal_status)
        latency_samples.append(
            adb(
                args.serial,
                "shell",
                "dumpsys",
                "SurfaceFlinger",
                "--latency",
                surface_arg,
                check=False,
            )
        )
        time.sleep(min(args.sample_interval, max(0.0, deadline - time.monotonic())))
    return_code = perfetto.wait(timeout=15) if perfetto.poll() is None else perfetto.returncode
    stderr = perfetto.stderr.read() if perfetto.stderr else ""

    verify_active_title_id(args.serial, args.package, pid, expected_title_id)
    raw_latency = adb(
        args.serial, "shell", "dumpsys", "SurfaceFlinger", "--latency", surface_arg
    )
    latency_samples.append(raw_latency)
    trace_path = output / "trace.pftrace"
    invalid_trace_name: str | None = None
    if return_code == 0:
        adb(args.serial, "pull", REMOTE_TRACE, str(trace_path))
        perfetto_available, perfetto_reason = validate_perfetto_trace(trace_path, args.package)
        if not perfetto_available:
            invalid_trace_name = "trace.invalid.pftrace"
            trace_path.rename(output / invalid_trace_name)
    else:
        perfetto_available = False
        perfetto_reason = f"perfetto_failed_{return_code}: {stderr.strip()}"
    adb(args.serial, "shell", "rm", "-f", REMOTE_TRACE)
    (output / "surfaceflinger-latency.txt").write_text(raw_latency + "\n", encoding="utf-8")
    latency_samples_path = output / "surfaceflinger-latency-samples.json"
    latency_samples_path.write_text(
        json.dumps(latency_samples, indent=2) + "\n", encoding="utf-8"
    )

    refresh_period, timestamps, frametimes = merge_surfaceflinger_latency(latency_samples)
    if not frametimes:
        raise CaptureError("SurfaceFlinger returned no frame intervals for the selected layer")
    summary = summarize_frametimes(frametimes)
    gpu_temperatures = [
        float(sample["temperature_c"])
        for sample in kgsl_samples
        if "temperature_c" in sample
    ]
    busy_samples = [
        (int(sample["busy_us"]), int(sample["total_us"]))
        for sample in kgsl_samples
        if "busy_us" in sample and "total_us" in sample
    ]
    busy_delta_percent = 0.0
    if len(busy_samples) >= 2:
        busy_delta = busy_samples[-1][0] - busy_samples[0][0]
        total_delta = busy_samples[-1][1] - busy_samples[0][1]
        if total_delta > 0:
            busy_delta_percent = busy_delta / total_delta * 100.0
    summary.update(
        {
            "rss_max_kib": max(rss_samples, default=0),
            "temperature_max_c": max(gpu_temperatures, default=0.0),
            "gpu_utilization_max_percent": max(
                (float(sample.get("utilization_percent", 0)) for sample in kgsl_samples),
                default=0.0,
            ),
            "gpu_frequency_max_hz": max(
                (int(sample.get("frequency_hz", 0)) for sample in kgsl_samples), default=0
            ),
            "gpu_busy_delta_percent": busy_delta_percent,
            "android_thermal_status_max": max(thermal_status_samples, default=-1),
        }
    )
    if not gpu_temperatures:
        raise CaptureError("KGSL GPU temperature is unavailable")
    if not args.temperature_min_c <= gpu_temperatures[0] <= args.temperature_max_c:
        raise CaptureError("Measured starting GPU temperature is outside the selected band")
    if abs(gpu_temperatures[0] - args.initial_temperature_c) > 2.0:
        raise CaptureError("Declared and measured starting GPU temperatures differ by over 2 C")
    if max(gpu_temperatures) > args.temperature_max_c:
        raise CaptureError("GPU temperature exceeded the selected promotion band")
    if not thermal_status_samples or max(thermal_status_samples) != 0:
        raise CaptureError("Android thermal status was unavailable or nonzero")
    timestamp_path = output / "surfaceflinger-present-timestamps-ns.txt"
    timestamp_path.write_text("\n".join(map(str, timestamps)) + "\n", encoding="utf-8")

    device = {
        "manufacturer": adb(args.serial, "shell", "getprop", "ro.product.manufacturer"),
        "model": adb(args.serial, "shell", "getprop", "ro.product.model"),
        "build_fingerprint": adb(args.serial, "shell", "getprop", "ro.build.fingerprint"),
        "android_release": adb(args.serial, "shell", "getprop", "ro.build.version.release"),
        "resolution": args.resolution or adb(args.serial, "shell", "wm", "size"),
        "gpu_driver": args.gpu_driver
        or adb(args.serial, "shell", "getprop", "ro.gfx.driver.0", check=False),
    }
    manifest = {
        "schema": SCHEMA,
        "manifest_revision": MANIFEST_REVISION,
        "captured_at": datetime.now(timezone.utc).isoformat(),
        "run_id": args.run_id,
        "variant": args.variant,
        "title_id": expected_title_id,
        "package": args.package,
        "surface": surface,
        "duration_s": args.duration,
        "refresh_period_ns": refresh_period,
        "scenario": scenario,
        "scenario_sha256": scenario_digest(scenario),
        "runtime_identity": runtime_identity,
        "replay_sha256": args.replay_sha256.lower(),
        "experiment_key": args.experiment_key,
        "git": git_manifest(root),
        "local_apk": {
            "path": str(apk),
            "sha256": local_apk_sha256,
        },
        "installed_apk": installed_apk,
        "switch_firmware": args.switch_firmware,
        "game_version": args.game_version,
        "cache_state": args.cache_state,
        "cheat_set_sha256": args.cheat_set_sha256.lower(),
        "fan_mode": args.fan_mode,
        "initial_temperature_c": args.initial_temperature_c,
        "temperature_band_c": [args.temperature_min_c, args.temperature_max_c],
        "profile": args.profile,
        "device": device,
        "host": {"platform": platform.platform(), "python": platform.python_version()},
        "summary": summary,
        "thermal_sources": temperature_samples,
        "kgsl_samples": kgsl_samples,
        "android_thermal_status_samples": thermal_status_samples,
        "perfetto": {
            "available": perfetto_available,
            "unavailable_reason": perfetto_reason,
        },
        "artifacts": {
            "trace": trace_path.name if perfetto_available else None,
            "invalid_trace": invalid_trace_name,
            "surfaceflinger_raw": "surfaceflinger-latency.txt",
            "surfaceflinger_raw_samples": latency_samples_path.name,
            "present_timestamps": timestamp_path.name,
        },
    }
    (output / "manifest.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0


def run_configuration(manifest: dict[str, Any]) -> dict[str, Any]:
    device = manifest.get("device", {})
    git = manifest.get("git", {})
    local_apk = manifest.get("local_apk", {})
    installed_apk = manifest.get("installed_apk", {})
    runtime_identity = manifest.get("runtime_identity", {})
    return {
        "package": manifest.get("package"),
        "local_apk_sha256": local_apk.get("sha256"),
        "installed_apk_sha256": installed_apk.get("sha256"),
        "installed_apk_version": installed_apk.get("version"),
        "git_head": git.get("head"),
        "source_tree_sha256": git.get("source_tree_sha256"),
        "switch_firmware": manifest.get("switch_firmware"),
        "profile": manifest.get("profile"),
        "manufacturer": device.get("manufacturer"),
        "model": device.get("model"),
        "build_fingerprint": device.get("build_fingerprint"),
        "android_release": device.get("android_release"),
        "resolution": device.get("resolution"),
        "gpu_driver": device.get("gpu_driver"),
        "game_version": manifest.get("game_version"),
        "cache_state": manifest.get("cache_state"),
        "cheat_set_sha256": manifest.get("cheat_set_sha256"),
        "fan_mode": manifest.get("fan_mode"),
        "temperature_band_c": manifest.get("temperature_band_c"),
        "replay_sha256": manifest.get("replay_sha256"),
        "runtime_identity.package_version": runtime_identity.get("package_version"),
        "runtime_identity.requested_workers": runtime_identity.get("requested_workers"),
        "runtime_identity.effective_workers": runtime_identity.get("effective_workers"),
        "runtime_identity.worker_reason": runtime_identity.get("worker_reason"),
        "runtime_identity.async_gpu": runtime_identity.get("async_gpu"),
        "runtime_identity.async_shaders": runtime_identity.get("async_shaders"),
        "runtime_identity.async_presentation": runtime_identity.get("async_presentation"),
        "runtime_identity.descriptor_buffer_available": runtime_identity.get(
            "descriptor_buffer_available"
        ),
        "runtime_identity.presentation_target": runtime_identity.get("presentation_target"),
    }


def validate_manifest(manifest: dict[str, Any]) -> None:
    if manifest.get("schema") != SCHEMA:
        raise CaptureError(f"All manifests must use schema {SCHEMA}")
    if manifest.get("manifest_revision") != MANIFEST_REVISION:
        raise CaptureError("Only manifest_revision=2 is promotion-eligible")
    local_hash = manifest.get("local_apk", {}).get("sha256")
    installed_hash = manifest.get("installed_apk", {}).get("sha256")
    if not local_hash or local_hash != installed_hash:
        raise CaptureError("Local and installed APK hashes must match")
    identity = manifest.get("runtime_identity")
    if not isinstance(identity, dict) or identity.get("schema") != RUNTIME_SCHEMA:
        raise CaptureError("A revision-2 manifest requires runtime identity")
    missing_runtime = sorted(REQUIRED_RUNTIME_FIELDS - identity.keys())
    if missing_runtime:
        raise CaptureError("Runtime identity is incomplete: " + ", ".join(missing_runtime))
    if str(identity.get("title_id", "")).lower() != str(manifest.get("title_id", "")).lower():
        raise CaptureError("Runtime identity Title ID mismatch")
    if identity.get("replay_sha256") != manifest.get("replay_sha256"):
        raise CaptureError("Runtime identity replay mismatch")
    if identity.get("package_version") != manifest.get("installed_apk", {}).get("version"):
        raise CaptureError("Runtime identity APK version mismatch")
    if identity.get("replay_state") not in {"RUNNING", "COMPLETED"}:
        raise CaptureError("Runtime replay did not run to completion")
    if identity.get("session_state") != "RUNNING" or not identity.get("surface_attached"):
        raise CaptureError("Runtime identity does not describe an active rendered session")
    if int(identity.get("session_generation", 0)) <= 0 or int(identity.get("pid", 0)) <= 0:
        raise CaptureError("Runtime identity PID or generation is stale")
    experiment_key = manifest.get("experiment_key")
    if experiment_key not in EXPERIMENT_FIELDS:
        raise CaptureError("experiment_key must be workers or build")
    if not re.fullmatch(r"[0-9a-f]{64}", str(manifest.get("replay_sha256", ""))):
        raise CaptureError("Replay SHA-256 is invalid")
    if not re.fullmatch(r"[0-9a-f]{64}", str(manifest.get("cheat_set_sha256", ""))):
        raise CaptureError("Cheat-set SHA-256 is invalid")
    summary = manifest.get("summary", {})
    for name, value in summary.items():
        if isinstance(value, (int, float)) and not math.isfinite(float(value)):
            raise CaptureError(f"Summary metric {name} is not finite")
    initial_temperature = manifest.get("initial_temperature_c")
    if not isinstance(initial_temperature, (int, float)) or not math.isfinite(initial_temperature):
        raise CaptureError("Initial temperature is missing or non-finite")
    temperature_band = manifest.get("temperature_band_c")
    if (
        not isinstance(temperature_band, list)
        or len(temperature_band) != 2
        or not all(isinstance(value, (int, float)) for value in temperature_band)
        or not float(temperature_band[0]) <= float(initial_temperature) <= float(temperature_band[1])
    ):
        raise CaptureError("Initial temperature is outside the calibrated band")


def load_manifests(paths: Iterable[str]) -> list[dict[str, Any]]:
    manifests = [json.loads(Path(path).read_text(encoding="utf-8")) for path in paths]
    for manifest in manifests:
        validate_manifest(manifest)
    if len(manifests) < 5:
        raise CaptureError("At least five runs are required")
    title_ids = {manifest.get("title_id") for manifest in manifests}
    scenarios = {manifest.get("scenario_sha256") for manifest in manifests}
    variants = {manifest.get("variant") for manifest in manifests}
    experiment_keys = {manifest.get("experiment_key") for manifest in manifests}
    if len(title_ids) != 1 or len(scenarios) != 1 or len(variants) != 1 or len(experiment_keys) != 1:
        raise CaptureError("Runs must have one Title ID, scenario, and variant")
    configurations = {
        json.dumps(run_configuration(manifest), sort_keys=True) for manifest in manifests
    }
    missing_configuration = [
        key
        for key, value in run_configuration(manifests[0]).items()
        if value is None or value == ""
    ]
    if missing_configuration:
        raise CaptureError(
            "Run configuration is incomplete: " + ", ".join(missing_configuration)
        )
    if len(configurations) != 1:
        raise CaptureError("Runs must use one APK, source tree, firmware, profile, and device setup")
    temperatures = [float(manifest["initial_temperature_c"]) for manifest in manifests]
    if max(temperatures) - min(temperatures) > 2.0:
        raise CaptureError("Run start temperatures are outside the calibrated 2 C band")
    return manifests


def aggregate(manifests: Sequence[dict[str, Any]]) -> dict[str, Any]:
    metric_names = (
        "median_fps",
        "frametime_p50_ms",
        "frametime_p95_ms",
        "frametime_p99_ms",
        "frame_count",
        "rss_max_kib",
        "temperature_max_c",
    )
    medians = {
        name: statistics.median(float(manifest["summary"][name]) for manifest in manifests)
        for name in metric_names
    }
    return {
        "schema": SCHEMA,
        "manifest_revision": MANIFEST_REVISION,
        "variant": manifests[0]["variant"],
        "title_id": manifests[0]["title_id"],
        "scenario_sha256": manifests[0]["scenario_sha256"],
        "configuration": run_configuration(manifests[0]),
        "experiment_key": manifests[0]["experiment_key"],
        "run_count": len(manifests),
        "median_of_runs": medians,
        "runs": [manifest["run_id"] for manifest in manifests],
    }


def summarize(args: argparse.Namespace) -> int:
    result = aggregate(load_manifests(args.manifests))
    Path(args.output).write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


def percentage_change(baseline: float, candidate: float, *, higher_is_better: bool) -> float:
    if baseline == 0:
        return 0.0
    raw = (candidate - baseline) / baseline * 100.0
    return raw if higher_is_better else -raw


def compare_summaries(baseline: dict[str, Any], candidate: dict[str, Any]) -> dict[str, Any]:
    for summary in (baseline, candidate):
        if (
            summary.get("schema") != SCHEMA
            or summary.get("manifest_revision") != MANIFEST_REVISION
            or summary.get("run_count", 0) < 5
        ):
            raise CaptureError("Comparison inputs must contain at least five v2 runs")
    for field in ("title_id", "scenario_sha256"):
        if baseline.get(field) != candidate.get(field):
            raise CaptureError(f"A/B inputs do not match on {field}")
    experiment_key = baseline.get("experiment_key")
    if experiment_key != candidate.get("experiment_key") or experiment_key not in EXPERIMENT_FIELDS:
        raise CaptureError("A/B inputs must declare one matching experiment key")
    baseline_configuration = baseline.get("configuration", {})
    candidate_configuration = candidate.get("configuration", {})
    differing_fields = {
        key
        for key in set(baseline_configuration) | set(candidate_configuration)
        if baseline_configuration.get(key) != candidate_configuration.get(key)
    }
    unauthorized = differing_fields - EXPERIMENT_FIELDS[experiment_key]
    if unauthorized:
        raise CaptureError("A/B controls drifted: " + ", ".join(sorted(unauthorized)))
    if not differing_fields:
        raise CaptureError("A/B inputs do not differ on the declared experiment key")

    old = baseline["median_of_runs"]
    new = candidate["median_of_runs"]
    changes = {
        "median_fps": percentage_change(old["median_fps"], new["median_fps"], higher_is_better=True),
        "frametime_p50_ms": percentage_change(
            old["frametime_p50_ms"], new["frametime_p50_ms"], higher_is_better=False
        ),
        "frametime_p95_ms": percentage_change(
            old["frametime_p95_ms"], new["frametime_p95_ms"], higher_is_better=False
        ),
        "frametime_p99_ms": percentage_change(
            old["frametime_p99_ms"], new["frametime_p99_ms"], higher_is_better=False
        ),
        "frame_count": percentage_change(
            old["frame_count"], new["frame_count"], higher_is_better=True
        ),
        "rss_max_kib": percentage_change(
            old["rss_max_kib"], new["rss_max_kib"], higher_is_better=False
        ),
        "temperature_max_c": percentage_change(
            old["temperature_max_c"], new["temperature_max_c"], higher_is_better=False
        ),
    }
    has_regression = any(change < -2.0 for change in changes.values())
    temperature_delta_c = new["temperature_max_c"] - old["temperature_max_c"]
    if temperature_delta_c > 2.0:
        has_regression = True
    has_required_gain = changes["median_fps"] >= 3.0 or (
        changes["frametime_p95_ms"] > 0.0 and changes["frametime_p99_ms"] > 0.0
    )
    return {
        "schema": SCHEMA,
        "baseline_variant": baseline["variant"],
        "candidate_variant": candidate["variant"],
        "change_percent_positive_is_better": changes,
        "thresholds": {"minimum_gain_percent": 3.0, "maximum_regression_percent": 2.0},
        "temperature_delta_c": temperature_delta_c,
        "verdict": "promote" if has_required_gain and not has_regression else "reject",
        "reason": (
            "regression_over_2_percent"
            if has_regression
            else "gain_or_tail_latency_improvement"
            if has_required_gain
            else "gain_below_threshold"
        ),
    }


def compare(args: argparse.Namespace) -> int:
    baseline = json.loads(Path(args.baseline).read_text(encoding="utf-8"))
    candidate = json.loads(Path(args.candidate).read_text(encoding="utf-8"))
    result = compare_summaries(baseline, candidate)
    Path(args.output).write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["verdict"] == "promote" else 2


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="opensw-performance-v2")
    subparsers = parser.add_subparsers(dest="command", required=True)

    capture_parser = subparsers.add_parser("capture")
    capture_parser.add_argument("--output", required=True)
    capture_parser.add_argument("--scenario", required=True)
    capture_parser.add_argument("--title-id", required=True)
    capture_parser.add_argument("--run-id", required=True)
    capture_parser.add_argument("--variant", required=True)
    capture_parser.add_argument("--switch-firmware", required=True)
    capture_parser.add_argument("--profile", required=True)
    capture_parser.add_argument("--package", default="com.remipelloux.opensw.profile")
    capture_parser.add_argument("--duration", type=int, default=60)
    capture_parser.add_argument("--sample-interval", type=float, default=1.0)
    capture_parser.add_argument("--serial")
    capture_parser.add_argument("--surface")
    capture_parser.add_argument("--apk", required=True)
    capture_parser.add_argument(
        "--runtime-identity",
        help="Existing identity JSON; defaults to the signed OpenSw Lab instrumentation bridge",
    )
    capture_parser.add_argument("--replay-sha256", required=True)
    capture_parser.add_argument("--game-version", required=True)
    capture_parser.add_argument("--cache-state", choices=("cold", "warm"), required=True)
    capture_parser.add_argument("--cheat-set-sha256", required=True)
    capture_parser.add_argument("--fan-mode", required=True)
    capture_parser.add_argument("--initial-temperature-c", type=float, required=True)
    capture_parser.add_argument("--temperature-min-c", type=float, required=True)
    capture_parser.add_argument("--temperature-max-c", type=float, required=True)
    capture_parser.add_argument("--experiment-key", choices=tuple(EXPERIMENT_FIELDS), required=True)
    capture_parser.add_argument("--gpu-driver")
    capture_parser.add_argument("--resolution")
    capture_parser.add_argument("--repo", default=".")
    capture_parser.set_defaults(func=capture)

    summarize_parser = subparsers.add_parser("summarize")
    summarize_parser.add_argument("--output", required=True)
    summarize_parser.add_argument("manifests", nargs="+")
    summarize_parser.set_defaults(func=summarize)

    compare_parser = subparsers.add_parser("compare")
    compare_parser.add_argument("--baseline", required=True)
    compare_parser.add_argument("--candidate", required=True)
    compare_parser.add_argument("--output", required=True)
    compare_parser.set_defaults(func=compare)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        return args.func(args)
    except (CaptureError, json.JSONDecodeError, OSError, subprocess.SubprocessError) as error:
        print(f"error: {error}", file=os.sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
