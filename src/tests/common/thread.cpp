// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include <catch2/catch_test_macros.hpp>

#include "common/adpf.h"
#include "common/thread.h"

TEST_CASE("Thread performance mode validates runtime policy", "[common][thread]") {
    const auto original = Common::GetThreadPerformanceMode();

    REQUIRE(Common::SetThreadPerformanceMode(Common::ThreadPerformanceMode::System));
    REQUIRE(Common::GetThreadPerformanceMode() == Common::ThreadPerformanceMode::System);
    REQUIRE(Common::SetThreadPerformanceMode(Common::ThreadPerformanceMode::Hints));
    REQUIRE(Common::GetThreadPerformanceMode() == Common::ThreadPerformanceMode::Hints);
    REQUIRE(Common::SetThreadPerformanceMode(Common::ThreadPerformanceMode::Hybrid));
    REQUIRE(Common::GetThreadPerformanceMode() == Common::ThreadPerformanceMode::Hybrid);

    REQUIRE_FALSE(
        Common::SetThreadPerformanceMode(static_cast<Common::ThreadPerformanceMode>(99)));
    REQUIRE(Common::GetThreadPerformanceMode() == Common::ThreadPerformanceMode::Hybrid);
    REQUIRE(Common::SetThreadPerformanceMode(original));
}

TEST_CASE("ADPF rejects idle gaps as frame work", "[common][thread]") {
    using namespace std::chrono_literals;

    REQUIRE(Common::ADPF::Detail::IsValidWorkDuration(8ms, 16ms));
    REQUIRE(Common::ADPF::Detail::IsValidWorkDuration(64ms, 16ms));
    REQUIRE_FALSE(Common::ADPF::Detail::IsValidWorkDuration(0ns, 16ms));
    REQUIRE_FALSE(Common::ADPF::Detail::IsValidWorkDuration(65ms, 16ms));
}
