#!/usr/bin/env python3
"""Host controller for the signed OpenSw Profile lab bridge."""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path

from opensw_performance_v2 import CaptureError, adb, parse_meminfo_rss_kib, run_lab_command

REMOTE_REPLAY_DIR = "/storage/emulated/0/Android/data/com.remipelloux.opensw.lab/files"
REMOTE_REPLAY_NAME = "opensw-input-replay.json"
PROFILE_PACKAGE = "com.remipelloux.opensw.profile"
EDEN_PACKAGE = "dev.eden.eden_emulator.nightly"

RESOLUTION_VALUES = {
    "0.25x": 0,
    "0.5x": 1,
    "0.75x": 2,
    "1x": 3,
    "1.25x": 4,
    "1.5x": 5,
    "2x": 6,
    "3x": 7,
    "4x": 8,
    "5x": 9,
    "6x": 10,
    "7x": 11,
    "8x": 12,
}
SCALING_FILTER_VALUES = {
    "nearest": 0,
    "bilinear": 1,
    "bicubic": 2,
    "gaussian": 3,
    "lanczos": 4,
    "scaleforce": 5,
    "fsr": 6,
    "area": 7,
    "zero-tangent": 8,
    "b-spline": 9,
    "mitchell": 10,
    "spline-1": 11,
    "mmpx": 12,
    "sgsr": 13,
    "sgsr-edge": 14,
}


def canonical_replay_sha256(replay: dict) -> str:
    lines = [
        str(replay["schema"]),
        str(replay["title_id"]).upper(),
        str(replay["game_version"]),
        str(replay["controller_id"]).lower(),
        str(replay.get("controller_port", 0)),
        str(replay["duration_ns"]),
    ]
    for event in replay["events"]:
        float_bits = struct.unpack("!i", struct.pack("!f", float(event["value"])))[0]
        lines.append(
            f"{event['timestamp_ns']}|{event['kind']}|{event['control']}|{float_bits}"
        )
    canonical = ("\n".join(lines) + "\n").encode()
    return hashlib.sha256(canonical).hexdigest()


def memory_snapshot(serial: str | None) -> dict:
    raw = adb(serial, "shell", "dumpsys", "meminfo", PROFILE_PACKAGE)
    native = 0
    views = 0
    activities = 0
    for line in raw.splitlines():
        fields = line.split()
        if len(fields) > 2 and fields[0:2] == ["Native", "Heap"]:
            native = int(fields[2])
        if "Views:" in line and "Activities:" in line:
            views = int(line.split("Views:", 1)[1].split()[0])
            activities = int(line.split("Activities:", 1)[1].split()[0])
    return {
        "elapsed_realtime_ms": int(adb(serial, "shell", "cat", "/proc/uptime").split(".")[0])
        * 1000,
        "rss_kib": parse_meminfo_rss_kib(raw),
        "native_heap_pss_kib": native,
        "views": views,
        "activities": activities,
    }


def require_no_eden(serial: str | None) -> None:
    if adb(serial, "shell", "pidof", EDEN_PACKAGE, check=False).strip():
        raise CaptureError("Official Eden is running; stop it manually before a lab cycle run")


def require_top_activity(serial: str | None, component: str) -> None:
    activities = adb(serial, "shell", "dumpsys", "activity", "activities")
    resumed = next((line for line in activities.splitlines() if "topResumedActivity=" in line), "")
    if component not in resumed:
        raise CaptureError(f"Expected top activity {component}, found {resumed.strip() or 'none'}")


def execute_cycles(args: argparse.Namespace) -> int:
    require_no_eden(args.serial)
    report = {
        "schema": "opensw-lifecycle-cycles-v1",
        "created_at": datetime.now(timezone.utc).isoformat(),
        "title_id": args.title_id.lower(),
        "cycles_requested": args.cycles,
        "cycles": [],
        "physical_rotation_covered": False,
    }
    stable_pid = ""
    for index in range(args.cycles):
        require_no_eden(args.serial)
        run_lab_command(
            args.serial,
            "launch",
            {
                "game_uri": args.game_uri,
                "title_id": args.title_id,
                "timeout_ms": str(args.timeout_ms),
            },
        )
        require_top_activity(
            args.serial,
            f"{PROFILE_PACKAGE}/org.yuzu.yuzu_emu.activities.EmulationActivity",
        )
        pid = adb(args.serial, "shell", "pidof", PROFILE_PACKAGE).strip()
        if not pid or " " in pid:
            raise CaptureError("OpenSw Profile does not have exactly one process")
        if stable_pid and pid != stable_pid:
            raise CaptureError(f"OpenSw Profile PID changed from {stable_pid} to {pid}")
        stable_pid = pid
        run_lab_command(args.serial, "pause", {"timeout_ms": str(args.timeout_ms)})
        run_lab_command(args.serial, "resume", {"timeout_ms": str(args.timeout_ms)})
        run_lab_command(args.serial, "stop", {"timeout_ms": str(args.timeout_ms)})
        require_top_activity(
            args.serial,
            f"{PROFILE_PACKAGE}/org.yuzu.yuzu_emu.ui.main.MainActivity",
        )
        cycle = {"index": index + 1, "pid": pid, "memory": {}}
        previous = 0.0
        for seconds in args.memory_seconds:
            time.sleep(max(0.0, seconds - previous))
            cycle["memory"][str(seconds)] = memory_snapshot(args.serial)
            previous = seconds
        report["cycles"].append(cycle)

    report["cycles_completed"] = len(report["cycles"])
    Path(args.output).write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


def execute(args: argparse.Namespace) -> int:
    if args.command == "cycle":
        return execute_cycles(args)
    command = args.command
    command_args: dict[str, str] = {}
    timeout_ms = getattr(args, "timeout_ms", None)
    if timeout_ms is not None:
        command_args["timeout_ms"] = str(timeout_ms)
    if args.command == "launch":
        command_args.update(game_uri=args.game_uri, title_id=args.title_id)
    elif args.command == "set-workers":
        command_args["workers"] = str(args.workers)
    elif args.command == "set-graphics":
        command_args.update(
            resolution=str(RESOLUTION_VALUES[args.resolution]),
            scaling_filter=str(SCALING_FILTER_VALUES[args.scaling_filter]),
            sharpening=str(args.sharpening),
        )
    elif args.command == "clear-cache":
        command_args["title_id"] = args.title_id
    elif args.command == "start-capture":
        command_args.update(title_id=args.title_id, mode=args.mode)
    elif args.command == "start-replay":
        replay = Path(args.replay).resolve()
        if not replay.is_file() or replay.stat().st_size > 2 * 1024 * 1024:
            raise CaptureError("Replay must be an existing JSON file no larger than 2 MiB")
        replay_payload = json.loads(replay.read_text(encoding="utf-8"))
        replay_sha256 = canonical_replay_sha256(replay_payload)
        replay_payload["sha256"] = replay_sha256
        adb(args.serial, "shell", "mkdir", "-p", REMOTE_REPLAY_DIR)
        with tempfile.NamedTemporaryFile("w", suffix=".json", encoding="utf-8") as prepared:
            json.dump(replay_payload, prepared, separators=(",", ":"))
            prepared.flush()
            adb(
                args.serial,
                "push",
                prepared.name,
                f"{REMOTE_REPLAY_DIR}/{REMOTE_REPLAY_NAME}",
            )
        command_args["replay_file"] = REMOTE_REPLAY_NAME
    elif args.command in {"enable-cheat", "disable-cheat"}:
        command_args.update(
            name=args.name,
            enabled=str(args.command == "enable-cheat").lower(),
        )
        command = "set-cheat"

    try:
        result = run_lab_command(args.serial, command, command_args)
    finally:
        if args.command == "start-replay":
            adb(
                args.serial,
                "shell",
                "rm",
                "-f",
                f"{REMOTE_REPLAY_DIR}/{REMOTE_REPLAY_NAME}",
                check=False,
            )
    if args.command == "finish-capture":
        report = result.get("value")
        if not isinstance(report, dict):
            raise CaptureError("OpenSw Lab capture report is missing")
        output = Path(args.output).resolve()
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(
            json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        print(f"capture_report={output}")
    else:
        print(json.dumps(result, indent=2, sort_keys=True))
    if args.command == "start-replay":
        print(f"replay_sha256={replay_sha256}")
    return 0


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(prog="opensw-lab")
    result.add_argument("--serial")
    subparsers = result.add_subparsers(dest="command", required=True)
    subparsers.add_parser("runtime-identity")
    subparsers.add_parser("session-status")
    for command in ("pause", "resume", "stop"):
        child = subparsers.add_parser(command)
        child.add_argument("--timeout-ms", type=int, default=30_000)
    launch = subparsers.add_parser("launch")
    launch.add_argument("--game-uri", required=True)
    launch.add_argument("--title-id", required=True)
    launch.add_argument("--timeout-ms", type=int, default=120_000)
    workers = subparsers.add_parser("set-workers")
    workers.add_argument("workers", type=int)
    graphics = subparsers.add_parser("set-graphics")
    graphics.add_argument("--resolution", choices=RESOLUTION_VALUES, required=True)
    graphics.add_argument("--scaling-filter", choices=SCALING_FILTER_VALUES, required=True)
    graphics.add_argument("--sharpening", type=int, choices=range(0, 101), default=0)
    cache = subparsers.add_parser("clear-cache")
    cache.add_argument("--title-id", required=True)
    replay = subparsers.add_parser("start-replay")
    replay.add_argument("replay")
    subparsers.add_parser("cancel-replay")
    capture = subparsers.add_parser("start-capture")
    capture.add_argument("--title-id", required=True)
    capture.add_argument("--mode", required=True)
    finish_capture = subparsers.add_parser("finish-capture")
    finish_capture.add_argument("--output", required=True)
    subparsers.add_parser("list-cheats")
    for command in ("enable-cheat", "disable-cheat"):
        cheat = subparsers.add_parser(command)
        cheat.add_argument("name")
    cycle = subparsers.add_parser("cycle")
    cycle.add_argument("--game-uri", required=True)
    cycle.add_argument("--title-id", required=True)
    cycle.add_argument("--cycles", type=int, choices=range(1, 31), default=6)
    cycle.add_argument("--timeout-ms", type=int, default=120_000)
    cycle.add_argument("--memory-seconds", type=float, nargs="+", default=[10.0, 30.0])
    cycle.add_argument("--output", required=True)
    return result


def main() -> int:
    try:
        return execute(parser().parse_args())
    except CaptureError as error:
        print(f"error: {error}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
