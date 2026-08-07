// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include <array>
#include <cstdint>

#include <catch2/catch_test_macros.hpp>

#include "video_core/renderer_vulkan/line_loop_utils.h"

TEST_CASE("Line loop expands only drawable loops", "[video_core][vulkan]") {
    CHECK(Vulkan::LineLoop::ExpandedIndexCount(0) == 0);
    CHECK(Vulkan::LineLoop::ExpandedIndexCount(1) == 1);
    CHECK(Vulkan::LineLoop::ExpandedIndexCount(2) == 3);
    CHECK(Vulkan::LineLoop::ExpandedIndexCount(5) == 6);
    CHECK(Vulkan::LineLoop::ExpandedIndexCount(std::numeric_limits<std::uint32_t>::max()) ==
          std::numeric_limits<std::uint32_t>::max());
}

TEST_CASE("Line loop sequential indices close on the first vertex", "[video_core][vulkan]") {
    std::array<std::uint16_t, 5> indices{};
    Vulkan::LineLoop::GenerateSequential<std::uint16_t>(indices);
    CHECK(indices == std::array<std::uint16_t, 5>{0, 1, 2, 3, 0});
}
