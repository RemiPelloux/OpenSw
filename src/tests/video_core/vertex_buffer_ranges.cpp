// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include <catch2/catch_test_macros.hpp>

#include "video_core/buffer_cache/vertex_buffer_ranges.h"

TEST_CASE("Vertex buffer ranges bind sparse dirty enabled slots", "[video_core][vulkan]") {
    const auto ranges = VideoCommon::BuildDirtyVertexBufferRanges(0b10110101, 0b11111111);
    REQUIRE(ranges.size() == 4);
    CHECK(ranges[0] == VideoCommon::VertexBufferRange{0, 1});
    CHECK(ranges[1] == VideoCommon::VertexBufferRange{2, 1});
    CHECK(ranges[2] == VideoCommon::VertexBufferRange{4, 2});
    CHECK(ranges[3] == VideoCommon::VertexBufferRange{7, 1});
}

TEST_CASE("Vertex buffer ranges exclude null and clean slots", "[video_core][vulkan]") {
    CHECK(VideoCommon::BuildDirtyVertexBufferRanges(0, ~0U).empty());
    CHECK(VideoCommon::BuildDirtyVertexBufferRanges(~0U, 0).empty());
    const auto ranges = VideoCommon::BuildDirtyVertexBufferRanges(0b111111, 0b100001);
    REQUIRE(ranges.size() == 2);
    CHECK(ranges[0] == VideoCommon::VertexBufferRange{0, 1});
    CHECK(ranges[1] == VideoCommon::VertexBufferRange{5, 1});
}

TEST_CASE("Vertex buffer ranges are draw-type and extension independent",
          "[video_core][vulkan]") {
    const auto non_indexed = VideoCommon::BuildDirtyVertexBufferRanges(0b1110, 0b1111);
    const auto indexed = VideoCommon::BuildDirtyVertexBufferRanges(0b1110, 0b1111);
    CHECK(non_indexed == indexed);
    REQUIRE(non_indexed.size() == 1);
    CHECK(non_indexed[0] == VideoCommon::VertexBufferRange{1, 3});
}
