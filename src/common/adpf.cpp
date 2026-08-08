// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include "common/adpf.h"

#ifdef __ANDROID__

#include <algorithm>
#include <array>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <optional>
#include <vector>

#include <dlfcn.h>
#include <unistd.h>

#include "common/logging.h"

namespace Common::ADPF {

namespace {

constexpr std::chrono::nanoseconds DEFAULT_TARGET = std::chrono::nanoseconds{16'666'667};

struct AHintManager;
struct AHintSession;

using PFN_GetManager = AHintManager* (*)();
using PFN_CreateSession = AHintSession* (*)(AHintManager*, const s32*, size_t, s64);
using PFN_CloseSession = void (*)(AHintSession*);
using PFN_UpdateTarget = int (*)(AHintSession*, s64);
using PFN_ReportActual = int (*)(AHintSession*, s64);
using PFN_SetThreads = int (*)(AHintSession*, const pid_t*, size_t);
using PFN_SetPowerEfficiency = int (*)(AHintSession*, bool);

struct Api {
    PFN_GetManager get_manager = nullptr;
    PFN_CreateSession create_session = nullptr;
    PFN_CloseSession close_session = nullptr;
    PFN_UpdateTarget update_target = nullptr;
    PFN_ReportActual report_actual = nullptr;
    PFN_SetThreads set_threads = nullptr;
    PFN_SetPowerEfficiency set_power_efficiency = nullptr;
    AHintManager* manager = nullptr;
    bool usable = false;
};

const Api& Resolve() {
    static const Api api = [] {
        Api resolved;
        void* library = dlopen("libandroid.so", RTLD_NOW);
        if (library == nullptr) {
            LOG_INFO(Common, "libandroid.so unavailable, ADPF is disabled");
            return resolved;
        }
        const auto load = [library](const char* name) { return dlsym(library, name); };

        resolved.get_manager = reinterpret_cast<PFN_GetManager>(load("APerformanceHint_getManager"));
        resolved.create_session =
            reinterpret_cast<PFN_CreateSession>(load("APerformanceHint_createSession"));
        resolved.close_session =
            reinterpret_cast<PFN_CloseSession>(load("APerformanceHint_closeSession"));
        resolved.update_target =
            reinterpret_cast<PFN_UpdateTarget>(load("APerformanceHint_updateTargetWorkDuration"));
        resolved.report_actual =
            reinterpret_cast<PFN_ReportActual>(load("APerformanceHint_reportActualWorkDuration"));
        resolved.set_threads =
            reinterpret_cast<PFN_SetThreads>(load("APerformanceHint_setThreads"));
        resolved.set_power_efficiency = reinterpret_cast<PFN_SetPowerEfficiency>(
            load("APerformanceHint_setPreferPowerEfficiency"));

        if (resolved.get_manager == nullptr || resolved.create_session == nullptr ||
            resolved.close_session == nullptr || resolved.update_target == nullptr ||
            resolved.report_actual == nullptr) {
            LOG_INFO(Common, "Performance hint API not exported, ADPF is disabled");
            return resolved;
        }

        resolved.manager = resolved.get_manager();
        if (resolved.manager == nullptr) {
            LOG_INFO(Common, "Device does not provide a performance hint manager");
            return resolved;
        }

        resolved.usable = true;
        LOG_INFO(Common, "ADPF available, setThreads {}, power efficiency {}",
                 resolved.set_threads != nullptr ? "yes" : "no",
                 resolved.set_power_efficiency != nullptr ? "yes" : "no");
        return resolved;
    }();
    return api;
}

struct SessionState {
    AHintSession* handle = nullptr;
    std::vector<pid_t> threads;
};

struct AdpfState {
    std::mutex mutex;
    std::array<SessionState, 2> sessions;
    std::atomic<s64> target_ns{DEFAULT_TARGET.count()};
    std::atomic<bool> render_active{};
};

thread_local std::chrono::steady_clock::time_point t_frame_work_start{};

AdpfState& State() {
    static AdpfState* const state = new AdpfState();
    return *state;
}

SessionState& StateOf(Session session) {
    return State().sessions[static_cast<size_t>(session)];
}

bool IsBackgroundUsable(const Api& api) {
    return api.set_power_efficiency != nullptr;
}

void CloseLocked(SessionState& state) {
    if (state.handle != nullptr) {
        Resolve().close_session(state.handle);
        state.handle = nullptr;
    }
}

AHintSession* CreateSessionFor(Session session, const std::vector<pid_t>& threads) {
    const Api& api = Resolve();
    const s64 target = session == Session::Render
                           ? State().target_ns.load(std::memory_order_relaxed)
                           : DEFAULT_TARGET.count();

    std::vector<s32> ids;
    ids.reserve(threads.size());
    for (const pid_t tid : threads) {
        ids.push_back(static_cast<s32>(tid));
    }

    AHintSession* handle = api.create_session(api.manager, ids.data(), ids.size(), target);
    if (handle == nullptr) {
        return nullptr;
    }
    if (session == Session::Background && api.set_power_efficiency != nullptr) {
        api.set_power_efficiency(handle, true);
    }
    return handle;
}

bool ReplaceThreadsLocked(Session session, SessionState& state, std::vector<pid_t> threads) {
    if (threads == state.threads) {
        return state.handle != nullptr || threads.empty();
    }
    if (threads.empty()) {
        CloseLocked(state);
        state.threads.clear();
        if (session == Session::Render) {
            State().render_active.store(false, std::memory_order_release);
        }
        return true;
    }

    const Api& api = Resolve();
    if (state.handle != nullptr && api.set_threads != nullptr) {
        if (api.set_threads(state.handle, threads.data(), threads.size()) == 0) {
            state.threads = std::move(threads);
            if (session == Session::Render) {
                State().render_active.store(true, std::memory_order_release);
            }
            return true;
        }
    }

    AHintSession* const replacement = CreateSessionFor(session, threads);
    if (replacement == nullptr) {
        LOG_WARNING(Common,
                    "Could not update the performance hint session to {} threads, falling back",
                    threads.size());
        return false;
    }
    CloseLocked(state);
    state.handle = replacement;
    state.threads = std::move(threads);
    if (session == Session::Render) {
        State().render_active.store(true, std::memory_order_release);
    }
    return true;
}

} // Anonymous namespace

bool IsSessionSupported(Session session) {
    const Api& api = Resolve();
    if (!api.usable) {
        return false;
    }
    if (session == Session::Background && !IsBackgroundUsable(api)) {
        return false;
    }
    return true;
}

bool AddCurrentThread(Session session) {
    if (!IsSessionSupported(session)) {
        return false;
    }

    const pid_t tid = gettid();
    AdpfState& global = State();
    std::scoped_lock lock{global.mutex};
    std::optional<Session> previous_session;

    for (size_t i = 0; i < global.sessions.size(); ++i) {
        SessionState& state = global.sessions[i];
        if (static_cast<size_t>(session) == i) {
            continue;
        }
        const auto it = std::find(state.threads.begin(), state.threads.end(), tid);
        if (it != state.threads.end()) {
            std::vector<pid_t> threads = state.threads;
            std::erase(threads, tid);
            if (!ReplaceThreadsLocked(static_cast<Session>(i), state, std::move(threads))) {
                return false;
            }
            previous_session = static_cast<Session>(i);
        }
    }

    SessionState& state = StateOf(session);
    if (std::find(state.threads.begin(), state.threads.end(), tid) != state.threads.end()) {
        return state.handle != nullptr;
    }
    std::vector<pid_t> threads = state.threads;
    threads.push_back(tid);
    if (ReplaceThreadsLocked(session, state, std::move(threads))) {
        return true;
    }
    if (previous_session) {
        SessionState& previous = StateOf(*previous_session);
        std::vector<pid_t> rollback = previous.threads;
        rollback.push_back(tid);
        ReplaceThreadsLocked(*previous_session, previous, std::move(rollback));
    }
    return false;
}

void RemoveCurrentThread() {
    if (!Resolve().usable) {
        return;
    }
    const pid_t tid = gettid();
    AdpfState& global = State();
    std::scoped_lock lock{global.mutex};
    for (size_t i = 0; i < global.sessions.size(); ++i) {
        SessionState& state = global.sessions[i];
        if (std::find(state.threads.begin(), state.threads.end(), tid) != state.threads.end()) {
            std::vector<pid_t> threads = state.threads;
            std::erase(threads, tid);
            ReplaceThreadsLocked(static_cast<Session>(i), state, std::move(threads));
        }
    }
}

void SetTargetWorkDuration(std::chrono::nanoseconds target) {
    const Api& api = Resolve();
    if (!api.usable || target.count() <= 0) {
        return;
    }
    AdpfState& global = State();
    if (global.target_ns.exchange(target.count(), std::memory_order_relaxed) == target.count()) {
        return;
    }
    std::scoped_lock lock{global.mutex};
    SessionState& state = StateOf(Session::Render);
    if (state.handle != nullptr) {
        api.update_target(state.handle, target.count());
    }
}

void BeginFrameWork() {
    if (!State().render_active.load(std::memory_order_acquire)) {
        return;
    }
    t_frame_work_start = std::chrono::steady_clock::now();
}

void ReportFrameWorkDuration() {
    if (!State().render_active.load(std::memory_order_acquire)) {
        t_frame_work_start = {};
        return;
    }
    const Api& api = Resolve();
    if (!api.usable || t_frame_work_start.time_since_epoch().count() == 0) {
        return;
    }

    const auto now = std::chrono::steady_clock::now();
    const s64 actual =
        std::chrono::duration_cast<std::chrono::nanoseconds>(now - t_frame_work_start).count();
    t_frame_work_start = {};
    if (actual <= 0) {
        return;
    }

    AdpfState& global = State();
    const std::chrono::nanoseconds target{global.target_ns.load(std::memory_order_relaxed)};
    if (!Detail::IsValidWorkDuration(std::chrono::nanoseconds{actual}, target)) {
        return;
    }

    std::scoped_lock lock{global.mutex};
    SessionState& state = StateOf(Session::Render);
    if (state.handle != nullptr) {
        api.report_actual(state.handle, actual);
    }
}

void Shutdown() {
    if (!Resolve().usable) {
        return;
    }
    AdpfState& global = State();
    std::scoped_lock lock{global.mutex};
    for (SessionState& state : global.sessions) {
        CloseLocked(state);
        state.threads.clear();
    }
    global.render_active.store(false, std::memory_order_release);
    t_frame_work_start = {};
}

} // namespace Common::ADPF

#else

namespace Common::ADPF {

bool IsSessionSupported(Session) {
    return false;
}

bool AddCurrentThread(Session) {
    return false;
}

void RemoveCurrentThread() {}

void SetTargetWorkDuration(std::chrono::nanoseconds) {}

void BeginFrameWork() {}

void ReportFrameWorkDuration() {}

void Shutdown() {}

} // namespace Common::ADPF

#endif
