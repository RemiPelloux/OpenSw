// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include <catch2/catch_test_macros.hpp>
#include <atomic>
#include <thread>

#include "core/core.h"
#include "core/memory/cheat_engine.h"
#include "core/memory/dmnt_cheat_vm.h"

namespace {
class NullCheatCallbacks final : public Core::Memory::DmntCheatVm::Callbacks {
public:
    void MemoryReadUnsafe(VAddr, void*, u64) override {}
    void MemoryWriteUnsafe(VAddr, const void*, u64) override {}
    u64 HidKeysDown() override {
        return 0;
    }
    void PauseProcess() override {}
    void ResumeProcess() override {}
    void DebugLog(u8, u64) override {}
    void CommandLog(std::string_view) override {}
};
} // namespace

TEST_CASE("Cheat parser locks Mastercode on and defaults regular cheats off", "[core][cheats]") {
    constexpr std::string_view text = R"(
{Mastercode}
04000000 00000000 00000001
[Shiny Pokemon On]
04000000 00000004 00000001
)";

    const Core::Memory::TextCheatParser parser;
    const auto cheats = parser.Parse(text);

    REQUIRE(cheats.size() == 2);
    CHECK(cheats[0].enabled);
    CHECK_FALSE(cheats[1].enabled);
    CHECK(cheats[0].definition.num_opcodes == 3);
    CHECK(cheats[1].definition.num_opcodes == 3);
}

TEST_CASE("Cheat parser rejects a section larger than 0x100 opcodes", "[core][cheats]") {
    std::string text = "[Too large]\n";
    for (std::size_t index = 0; index <= 0x100; ++index) {
        text += "04000000 ";
    }

    const Core::Memory::TextCheatParser parser;
    CHECK(parser.Parse(text).empty());
}

TEST_CASE("Cheat parser rejects truncated input", "[core][cheats]") {
    const Core::Memory::TextCheatParser parser;

    CHECK(parser.Parse("[Missing bracket\n04000000").empty());
    CHECK(parser.Parse("[Short opcode]\n0400").empty());
}

TEST_CASE("Cheat VM enforces the 0x400 active opcode limit", "[core][cheats]") {
    Core::Memory::DmntCheatVm vm{std::make_unique<NullCheatCallbacks>()};
    std::vector<Core::Memory::CheatEntry> entries(4);
    for (auto& entry : entries) {
        entry.enabled = true;
        entry.definition.num_opcodes = 0x100;
    }

    CHECK(vm.LoadProgram(entries));
    CHECK(vm.GetProgramSize() == 0x400);
    entries.push_back(entries.back());
    CHECK_FALSE(vm.LoadProgram(entries));
    CHECK(vm.GetProgramSize() == 0);
}

TEST_CASE("Concurrent cheat toggles preserve a coherent snapshot", "[core][cheats]") {
    Core::System system;
    std::vector<Core::Memory::CheatEntry> entries(9);
    entries[0].enabled = true;
    entries[0].cheat_id = 0;
    entries[0].definition.num_opcodes = 1;
    for (u32 index = 1; index < entries.size(); ++index) {
        entries[index].cheat_id = index;
        entries[index].definition.num_opcodes = 1;
    }
    Core::Memory::CheatEngine engine{system, entries, {}};

    std::atomic_bool all_toggles_succeeded{true};
    std::vector<std::thread> workers;
    for (u32 index = 1; index < entries.size(); ++index) {
        workers.emplace_back([&engine, &all_toggles_succeeded, index] {
            for (u32 iteration = 0; iteration < 100; ++iteration) {
                if (!engine.SetCheatEnabled(index, iteration % 2 == 0)) {
                    all_toggles_succeeded.store(false, std::memory_order_relaxed);
                }
            }
        });
    }
    for (auto& worker : workers) {
        worker.join();
    }

    const auto snapshot = engine.GetLoadedCheats();
    CHECK(all_toggles_succeeded.load(std::memory_order_relaxed));
    REQUIRE(snapshot.size() == entries.size());
    CHECK(snapshot.front().enabled);
    CHECK(snapshot.front().is_master);
    for (std::size_t index = 1; index < snapshot.size(); ++index) {
        CHECK_FALSE(snapshot[index].enabled);
    }
}
