// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include <catch2/catch_test_macros.hpp>

#include "video_core/renderer_vulkan/vk_pipeline_workers.h"
#include "video_core/renderer_vulkan/vk_scheduler.h"

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

TEST_CASE("Descriptor buffer offsets skip only identical active tuples",
          "[video_core][vulkan]") {
    Vulkan::DescriptorBufferOffsetState state;
    const auto layout_a = reinterpret_cast<VkPipelineLayout>(static_cast<uintptr_t>(1));
    const auto layout_b = reinterpret_cast<VkPipelineLayout>(static_cast<uintptr_t>(2));

    CHECK(state.Update(VK_PIPELINE_BIND_POINT_GRAPHICS, layout_a, 3, 64));
    CHECK_FALSE(state.Update(VK_PIPELINE_BIND_POINT_GRAPHICS, layout_a, 3, 64));
    CHECK(state.Update(VK_PIPELINE_BIND_POINT_GRAPHICS, layout_a, 3, 128));
    CHECK(state.Update(VK_PIPELINE_BIND_POINT_GRAPHICS, layout_a, 4, 128));
    CHECK(state.Update(VK_PIPELINE_BIND_POINT_GRAPHICS, layout_b, 4, 128));
    CHECK(state.Update(VK_PIPELINE_BIND_POINT_COMPUTE, layout_b, 4, 128));
    state.Reset();
    CHECK(state.Update(VK_PIPELINE_BIND_POINT_COMPUTE, layout_b, 4, 128));
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
