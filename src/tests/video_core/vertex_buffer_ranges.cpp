// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include <catch2/catch_test_macros.hpp>

#include "video_core/buffer_cache/vertex_buffer_ranges.h"

TEST_CASE("Vertex buffer ranges keep widely separated slots sparse", "[video_core][vulkan]") {
    const auto plan = VideoCommon::BuildAdaptiveVertexBufferRanges(0b10000001, 0b11111111);
    const auto ranges = plan.Ranges();
    REQUIRE(ranges.size() == 2);
    CHECK(ranges[0] == VideoCommon::VertexBufferRange{0, 1});
    CHECK(ranges[1] == VideoCommon::VertexBufferRange{7, 1});
}

TEST_CASE("Vertex buffer ranges exclude null and clean slots", "[video_core][vulkan]") {
    CHECK(VideoCommon::BuildAdaptiveVertexBufferRanges(0, ~0U).Ranges().empty());
    CHECK(VideoCommon::BuildAdaptiveVertexBufferRanges(~0U, 0).Ranges().empty());
    const auto plan = VideoCommon::BuildAdaptiveVertexBufferRanges(0b111111, 0b100001);
    const auto ranges = plan.Ranges();
    REQUIRE(ranges.size() == 2);
    CHECK(ranges[0] == VideoCommon::VertexBufferRange{0, 1});
    CHECK(ranges[1] == VideoCommon::VertexBufferRange{5, 1});
}

TEST_CASE("Vertex buffer ranges coalesce inexpensive gaps", "[video_core][vulkan]") {
    const auto plan = VideoCommon::BuildAdaptiveVertexBufferRanges(0b101, 0b111);
    const auto ranges = plan.Ranges();
    REQUIRE(ranges.size() == 1);
    CHECK(ranges[0] == VideoCommon::VertexBufferRange{0, 3});
}

TEST_CASE("Vertex buffer range planning has no heap-sized result", "[video_core][vulkan]") {
    const auto plan = VideoCommon::BuildAdaptiveVertexBufferRanges(~0U, ~0U);
    const auto ranges = plan.Ranges();
    REQUIRE(ranges.size() == 1);
    CHECK(ranges[0] == VideoCommon::VertexBufferRange{0, 32});
}
