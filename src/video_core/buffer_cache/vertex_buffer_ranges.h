// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#pragma once

#include <array>
#include <bit>
#include <compare>
#include <span>

#include "common/common_types.h"

namespace VideoCommon {

struct VertexBufferRange {
    u32 first;
    u32 count;

    auto operator<=>(const VertexBufferRange&) const = default;
};

struct VertexBufferRangePlan {
    std::array<VertexBufferRange, 32> storage;
    u32 count{};

    std::span<const VertexBufferRange> Ranges() const {
        return std::span{storage.data(), count};
    }
};

inline VertexBufferRangePlan BuildAdaptiveVertexBufferRanges(u32 enabled_mask, u32 dirty_mask) {
    VertexBufferRangePlan plan;
    u32 remaining = enabled_mask & dirty_mask;
    while (remaining != 0) {
        const u32 first = std::countr_zero(remaining);
        u32 count = 1;
        while (first + count < 32 && (remaining & (1U << (first + count))) != 0) {
            ++count;
        }
        plan.storage[plan.count++] = {first, count};
        const u32 range_mask = count == 32 ? ~0U : ((1U << count) - 1U) << first;
        remaining &= ~range_mask;
    }
    if (plan.count <= 1) {
        return plan;
    }

    const u32 active = enabled_mask & dirty_mask;
    const u32 first = std::countr_zero(active);
    const u32 dense_count = 32U - std::countl_zero(active) - first;
    const u32 sparse_slots = std::popcount(active);
    // Approximate the fixed command-stream cost so small gaps are cheaper than extra calls.
    constexpr u32 command_cost_in_slots = 2;
    const u32 sparse_cost = sparse_slots + plan.count * command_cost_in_slots;
    const u32 dense_cost = dense_count + command_cost_in_slots;
    if (dense_cost <= sparse_cost) {
        plan.storage[0] = {first, dense_count};
        plan.count = 1;
    }
    return plan;
}

} // namespace VideoCommon
