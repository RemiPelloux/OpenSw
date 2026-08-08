#!/usr/bin/env bash
# SPDX-FileCopyrightText: Copyright 2026 OpenSw Emulator Project
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
android_root="$repo_root/src/android"
output_dir="${1:-$repo_root/artifacts/opensw-profile-matrix}"

mkdir -p "$output_dir"

build_variant() {
    local cpu="$1"
    local lto="$2"
    local label="$3"

    "$android_root/gradlew" \
        -p "$android_root" \
        assembleOpenSwProfile \
        "-PopenswCpuPreset=$cpu" \
        "-PopenswLtoMode=$lto"

    local apk="$android_root/app/build/outputs/apk/openSw/profile/app-openSw-profile.apk"
    test -f "$apk"
    cp "$apk" "$output_dir/OpenSw-profile-$label.apk"
    shasum -a 256 "$output_dir/OpenSw-profile-$label.apk" \
        > "$output_dir/OpenSw-profile-$label.apk.sha256"
}

build_variant generic off generic
build_variant armv9 off armv9
build_variant generic thin thinlto
build_variant armv9 thin armv9-thinlto

echo "OpenSw profile matrix written to $output_dir"
