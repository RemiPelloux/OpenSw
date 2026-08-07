// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include <catch2/catch_test_macros.hpp>

#include "video_core/renderer_vulkan/vk_pipeline_workers.h"

TEST_CASE("Pipeline workers resolve Auto and explicit values", "[video_core][vulkan]") {
    const auto automatic = Vulkan::ResolvePipelineWorkers(0, 8, false);
    CHECK(automatic.effective == 7);
    CHECK(automatic.reason == Vulkan::PipelineWorkerReason::Auto);

    for (int requested = 2; requested <= 8; ++requested) {
        const auto explicit_value = Vulkan::ResolvePipelineWorkers(requested, 16, false);
        CHECK(explicit_value.effective == static_cast<size_t>(requested));
        CHECK(explicit_value.reason == Vulkan::PipelineWorkerReason::Explicit);
    }
}

TEST_CASE("Pipeline workers report caps and fallbacks", "[video_core][vulkan]") {
    const auto capped = Vulkan::ResolvePipelineWorkers(8, 8, false);
    CHECK(capped.effective == 7);
    CHECK(capped.reason == Vulkan::PipelineWorkerReason::HardwareCapped);

    const auto invalid = Vulkan::ResolvePipelineWorkers(1, 8, false);
    CHECK(invalid.requested == 1);
    CHECK(invalid.effective == 7);
    CHECK(invalid.reason == Vulkan::PipelineWorkerReason::InvalidFallback);

    const auto unknown_hardware = Vulkan::ResolvePipelineWorkers(0, 0, false);
    CHECK(unknown_hardware.effective == 1);
    CHECK(unknown_hardware.reason == Vulkan::PipelineWorkerReason::Auto);

    const auto serialized = Vulkan::ResolvePipelineWorkers(8, 16, true);
    CHECK(serialized.effective == 1);
    CHECK(serialized.reason == Vulkan::PipelineWorkerReason::DriverSerialized);
}
