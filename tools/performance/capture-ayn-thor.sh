#!/bin/sh
# SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
# SPDX-License-Identifier: GPL-3.0-or-later

set -eu

output_dir=${1:?usage: capture-ayn-thor.sh OUTPUT_DIR}
mkdir -p "$output_dir"

device_count=$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')
if [ "$device_count" -ne 1 ]; then
    echo "Expected exactly one authorized ADB device, found $device_count" >&2
    exit 1
fi

adb shell getprop > "$output_dir/getprop.txt"
adb shell dumpsys thermalservice > "$output_dir/thermal-before.txt"
adb shell pm path com.remipelloux.opensw > "$output_dir/opensw-package.txt"
adb shell pm path dev.eden.eden_emulator.nightly > "$output_dir/eden-package-readonly.txt"

adb shell perfetto -o /data/misc/perfetto-traces/opensw.perfetto-trace -t 60s sched freq idle am wm gfx view binder_driver hal dalvik memreclaim
adb pull /data/misc/perfetto-traces/opensw.perfetto-trace "$output_dir/opensw.perfetto-trace"

pid=$(adb shell pidof com.remipelloux.opensw | tr -d '\r')
if [ -n "$pid" ]; then
    adb shell simpleperf record -p "$pid" --duration 30 -o /data/local/tmp/opensw.simpleperf.data
    adb pull /data/local/tmp/opensw.simpleperf.data "$output_dir/opensw.simpleperf.data"
fi

adb shell dumpsys thermalservice > "$output_dir/thermal-after.txt"
