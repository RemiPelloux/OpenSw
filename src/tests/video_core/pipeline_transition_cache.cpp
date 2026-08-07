// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include <catch2/catch_test_macros.hpp>

#include "video_core/renderer_vulkan/vk_pipeline_transition_cache.h"

TEST_CASE("Pipeline transition cache keeps short sequences linear", "[video_core][vulkan]") {
    Vulkan::HybridTransitionCache<int, int*> cache;
    int values[4]{};
    for (int index = 0; index < 4; ++index) {
        REQUIRE(cache.Add(index, &values[index]));
    }
    CHECK_FALSE(cache.IsPromoted());
    CHECK(cache.Lookup(3).value == &values[3]);
    CHECK(cache.Lookup(3).probes == 4);
    CHECK(cache.Lookup(9).probes == 4);
}

TEST_CASE("Pipeline transition cache promotes and preserves pointers", "[video_core][vulkan]") {
    Vulkan::HybridTransitionCache<int, int*> cache;
    int values[6]{};
    for (int index = 0; index < 5; ++index) {
        REQUIRE(cache.Add(index, &values[index]));
    }
    REQUIRE(cache.IsPromoted());
    CHECK(cache.Size() == 5);
    CHECK(cache.Lookup(0).value == &values[0]);
    CHECK(cache.Lookup(4).value == &values[4]);
    CHECK(cache.Lookup(4).hashed);

    CHECK_FALSE(cache.Add(4, &values[5]));
    CHECK(cache.Lookup(4).value == &values[4]);
    REQUIRE(cache.Add(5, &values[5]));
    for (int repeat = 0; repeat < 100; ++repeat) {
        CHECK(cache.Lookup(repeat % 6).value == &values[repeat % 6]);
    }
}
