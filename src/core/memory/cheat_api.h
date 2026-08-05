// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#pragma once

#include <string>

#include "common/common_types.h"

namespace Core::Memory {

struct CheatContext {
    u64 title_id{};
    std::string build_id;
};

struct CheatSnapshot {
    u32 session_id{};
    std::string name;
    bool enabled{};
    bool is_master{};
    std::string fingerprint;
    std::string source;
};

} // namespace Core::Memory
