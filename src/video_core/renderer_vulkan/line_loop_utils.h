// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#pragma once

#include <limits>
#include <numeric>
#include <span>

#include "common/common_types.h"

namespace Vulkan::LineLoop {

constexpr u32 ExpandedIndexCount(u32 vertex_count) {
    return vertex_count > 1 && vertex_count != std::numeric_limits<u32>::max()
               ? vertex_count + 1
               : vertex_count;
}

template <typename Index>
void GenerateSequential(std::span<Index> output) {
    if (output.size() < 2) {
        return;
    }
    std::iota(output.begin(), output.end() - 1, Index{});
    output.back() = Index{};
}

} // namespace Vulkan::LineLoop
