// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

#pragma once

#include <chrono>

namespace Common::ADPF {

namespace Detail {
constexpr bool IsValidWorkDuration(std::chrono::nanoseconds actual,
                                   std::chrono::nanoseconds target) {
    return actual.count() > 0 && target.count() > 0 && actual <= target * 4;
}
} // namespace Detail

enum class Session {
    Render,
    Background,
};

bool IsSessionSupported(Session session);

bool AddCurrentThread(Session session);
void RemoveCurrentThread();

void SetTargetWorkDuration(std::chrono::nanoseconds target);

void BeginFrameWork();
void ReportFrameWorkDuration();

void Shutdown();

} // namespace Common::ADPF
