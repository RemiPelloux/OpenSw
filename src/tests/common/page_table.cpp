// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include <catch2/catch_test_macros.hpp>

#include "common/page_table.h"

TEST_CASE("PageTable: Reset releases session state", "[common]") {
    Common::PageTable page_table;

    for (const std::size_t address_bits : {24ULL, 25ULL}) {
        page_table.Resize(address_bits, 12);
        page_table.fastmem_arena = reinterpret_cast<u8*>(0x1000);

        REQUIRE(page_table.entries.size() == (1ULL << (address_bits - 12)));
        REQUIRE(page_table.GetAddressSpaceBits() == address_bits);

        page_table.Reset();

        REQUIRE(page_table.entries.size() == 0);
        REQUIRE(page_table.entries.data() == nullptr);
        REQUIRE(page_table.fastmem_arena == nullptr);
        REQUIRE(page_table.GetAddressSpaceBits() == 0);
    }
}
