// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#pragma once

#include "common/common_types.h"

namespace Vulkan {

enum class AndroidFlushAction {
    Dispatch,
    Defer,
    Flush,
    HardFlush,
};

constexpr u32 AndroidDrawFlushThreshold = 512;
constexpr u32 AndroidDrawHardFlushThreshold = 4096;

[[nodiscard]] constexpr AndroidFlushAction SelectAndroidFlushAction(
    u32 draw_count, bool render_pass_active) {
    if (draw_count < AndroidDrawFlushThreshold) {
        return AndroidFlushAction::Dispatch;
    }
    if (draw_count >= AndroidDrawHardFlushThreshold) {
        return AndroidFlushAction::HardFlush;
    }
    if (!render_pass_active) {
        return AndroidFlushAction::Flush;
    }
    return draw_count < AndroidDrawFlushThreshold + 4 ? AndroidFlushAction::Defer
                                                      : AndroidFlushAction::Dispatch;
}

} // namespace Vulkan
