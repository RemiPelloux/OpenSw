// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include <catch2/catch_test_macros.hpp>

#include "video_core/renderer_vulkan/vk_android_flush_policy.h"

TEST_CASE("Android draw flush waits for an active render pass", "[video_core][vulkan]") {
    using enum Vulkan::AndroidFlushAction;

    CHECK(Vulkan::SelectAndroidFlushAction(508, true) == Dispatch);
    CHECK(Vulkan::SelectAndroidFlushAction(512, true) == Defer);
    CHECK(Vulkan::SelectAndroidFlushAction(515, true) == Defer);
    CHECK(Vulkan::SelectAndroidFlushAction(519, true) == Dispatch);
    CHECK(Vulkan::SelectAndroidFlushAction(4092, true) == Dispatch);
}

TEST_CASE("Android draw flush submits at safe and hard boundaries", "[video_core][vulkan]") {
    using enum Vulkan::AndroidFlushAction;

    CHECK(Vulkan::SelectAndroidFlushAction(512, false) == Flush);
    CHECK(Vulkan::SelectAndroidFlushAction(876, false) == Flush);
    CHECK(Vulkan::SelectAndroidFlushAction(4096, true) == HardFlush);
    CHECK(Vulkan::SelectAndroidFlushAction(4096, false) == HardFlush);
}
