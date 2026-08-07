// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#pragma once

#include <cstddef>
#include <cstring>
#include <vector>

namespace Vulkan {

template <typename Entry>
bool UpdateDescriptorPayload(std::vector<Entry>& previous, const Entry* current, size_t count,
                             bool state_invalidated) {
    const bool changed =
        state_invalidated || previous.size() != count ||
        (count != 0 && std::memcmp(previous.data(), current, count * sizeof(Entry)) != 0);
    if (changed) {
        previous.assign(current, current + count);
    }
    return changed;
}

} // namespace Vulkan
