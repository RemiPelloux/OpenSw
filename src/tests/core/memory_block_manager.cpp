// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include <catch2/catch_test_macros.hpp>

#include "core/hle/kernel/k_memory_block_manager.h"

TEST_CASE("KMemoryBlockManager: Fastmem finalization skips free ranges", "[core][memory]") {
    REQUIRE_FALSE(Kernel::ShouldUnmapFastmemOnFinalize(Kernel::KMemoryState::Free));
    REQUIRE(Kernel::ShouldUnmapFastmemOnFinalize(Kernel::KMemoryState::Code));
    REQUIRE(Kernel::ShouldUnmapFastmemOnFinalize(Kernel::KMemoryState::Normal));
}
