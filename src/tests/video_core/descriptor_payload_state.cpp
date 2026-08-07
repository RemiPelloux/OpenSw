// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include <array>
#include <cstdint>
#include <vector>

#include <catch2/catch_test_macros.hpp>

#include "video_core/renderer_vulkan/vk_descriptor_payload_state.h"

TEST_CASE("Descriptor payload state skips identical consecutive payloads", "[video_core][vulkan]") {
    std::vector<std::uint64_t> previous;
    const std::array payload{std::uint64_t{1}, std::uint64_t{2}, std::uint64_t{3}};

    CHECK(Vulkan::UpdateDescriptorPayload(previous, payload.data(), payload.size(), true));
    CHECK_FALSE(Vulkan::UpdateDescriptorPayload(previous, payload.data(), payload.size(), false));
}

TEST_CASE("Descriptor payload state updates changed and invalidated payloads",
          "[video_core][vulkan]") {
    std::vector<std::uint64_t> previous;
    const std::array first{std::uint64_t{1}, std::uint64_t{2}};
    const std::array changed{std::uint64_t{1}, std::uint64_t{4}};

    REQUIRE(Vulkan::UpdateDescriptorPayload(previous, first.data(), first.size(), true));
    CHECK(Vulkan::UpdateDescriptorPayload(previous, changed.data(), changed.size(), false));
    CHECK(Vulkan::UpdateDescriptorPayload(previous, changed.data(), changed.size(), true));
}
