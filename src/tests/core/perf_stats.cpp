// SPDX-License-Identifier: GPL-2.0-or-later

#include <chrono>
#include <cmath>
#include <catch2/catch_test_macros.hpp>

#include "core/perf_stats.h"

TEST_CASE("PerfStats returns finite values without frames", "[core][perf]") {
    Core::PerfStats stats{0};
    const auto result = stats.GetAndResetStats(std::chrono::microseconds::zero());

    REQUIRE(result.system_fps == 0.0);
    REQUIRE(result.frametime == 0.0);
    REQUIRE(std::isfinite(result.average_game_fps));
    REQUIRE(std::isfinite(result.emulation_speed));
}

TEST_CASE("PerfStats reports a finite frame interval", "[core][perf]") {
    Core::PerfStats stats{0};
    stats.BeginSystemFrame();
    stats.EndSystemFrame();

    const auto result = stats.GetAndResetStats(std::chrono::microseconds{16'667});
    REQUIRE(std::isfinite(result.system_fps));
    REQUIRE(std::isfinite(result.frametime));
    REQUIRE(result.frametime >= 0.0);
}
