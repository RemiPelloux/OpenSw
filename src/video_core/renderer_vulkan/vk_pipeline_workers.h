// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#pragma once

#include <algorithm>
#include <cstddef>

namespace Vulkan {

enum class PipelineWorkerReason {
    Auto,
    Explicit,
    HardwareCapped,
    DriverSerialized,
    InvalidFallback,
};

struct PipelineWorkerResolution {
    int requested{};
    size_t effective{};
    PipelineWorkerReason reason{PipelineWorkerReason::Auto};
};

constexpr PipelineWorkerResolution ResolvePipelineWorkers(int requested,
                                                           unsigned hardware_concurrency,
                                                           bool driver_serialized) {
    const size_t usable_workers = hardware_concurrency == 0
                                      ? 1
                                      : std::max<size_t>(hardware_concurrency, 2) - 1;
    PipelineWorkerResolution result{requested, usable_workers, PipelineWorkerReason::Auto};
    if (requested != 0) {
        if (requested < 2 || requested > 8) {
            result.reason = PipelineWorkerReason::InvalidFallback;
        } else {
            result.effective = std::min(usable_workers, static_cast<size_t>(requested));
            result.reason = result.effective < static_cast<size_t>(requested)
                                ? PipelineWorkerReason::HardwareCapped
                                : PipelineWorkerReason::Explicit;
        }
    }
    if (driver_serialized) {
        result.effective = 1;
        result.reason = PipelineWorkerReason::DriverSerialized;
    }
    return result;
}

} // namespace Vulkan
