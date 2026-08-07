// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#pragma once

#include <bit>
#include <compare>
#include <vector>

#include "common/common_types.h"

namespace VideoCommon {

struct VertexBufferRange {
    u32 first{};
    u32 count{};

    auto operator<=>(const VertexBufferRange&) const = default;
};

inline std::vector<VertexBufferRange> BuildDirtyVertexBufferRanges(u32 enabled_mask,
                                                                    u32 dirty_mask) {
    std::vector<VertexBufferRange> ranges;
    u32 remaining = enabled_mask & dirty_mask;
    while (remaining != 0) {
        const u32 first = std::countr_zero(remaining);
        u32 count = 1;
        while (first + count < 32 && (remaining & (1U << (first + count))) != 0) {
            ++count;
        }
        ranges.push_back({first, count});
        const u32 range_mask = count == 32 ? ~0U : ((1U << count) - 1U) << first;
        remaining &= ~range_mask;
    }
    return ranges;
}

} // namespace VideoCommon
