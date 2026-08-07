// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

#pragma once

#include <array>
#ifdef OPENSW_PROFILE
#include <chrono>
#endif
#include <cstddef>
#include <type_traits>
#include <utility>

#include "common/common_types.h"

namespace Vulkan {

enum class PipelineProfilePhase {
    MaxwellTranslation,
    SpirvEmission,
    ShaderModule,
    VulkanPipeline,
};

enum class PresentationProfilePhase {
    FreeFrameWait,
    SchedulerWait,
    SwapchainAcquire,
    Present,
};

constexpr size_t PipelineProfileSnapshotSize = 17;
using PipelineProfileSnapshot = std::array<u64, PipelineProfileSnapshotSize>;

#ifdef OPENSW_PROFILE
void ProfilePipelineCacheHit();
void ProfilePipelineCacheMiss();
void ProfilePipelineCompilation();
void ProfilePipelineQueueDepth(size_t depth);
void ProfileSmallDrawWait();
void ProfilePipelineWait(u64 nanoseconds);
void ProfilePipelinePhase(PipelineProfilePhase phase, u64 nanoseconds);
void ProfilePresentationQueueDepth(size_t depth);
void ProfilePresentationPhase(PresentationProfilePhase phase, u64 nanoseconds);
void ResetPipelineProfile(u64 title_id);
void ReportPipelineProfile();
PipelineProfileSnapshot GetPipelineProfileSnapshot();
#else
inline void ProfilePipelineCacheHit() {}
inline void ProfilePipelineCacheMiss() {}
inline void ProfilePipelineCompilation() {}
inline void ProfilePipelineQueueDepth(size_t) {}
inline void ProfileSmallDrawWait() {}
inline void ProfilePipelineWait(u64) {}
inline void ProfilePipelinePhase(PipelineProfilePhase, u64) {}
inline void ProfilePresentationQueueDepth(size_t) {}
inline void ProfilePresentationPhase(PresentationProfilePhase, u64) {}
inline void ResetPipelineProfile(u64) {}
inline void ReportPipelineProfile() {}
inline PipelineProfileSnapshot GetPipelineProfileSnapshot() {
    return {};
}
#endif

template <typename Func>
auto MeasurePipelinePhase(PipelineProfilePhase phase, Func&& func) {
#ifdef OPENSW_PROFILE
    const auto start = std::chrono::steady_clock::now();
    auto result = std::forward<Func>(func)();
    const auto elapsed = std::chrono::steady_clock::now() - start;
    ProfilePipelinePhase(
        phase, static_cast<u64>(
                   std::chrono::duration_cast<std::chrono::nanoseconds>(elapsed).count()));
    return result;
#else
    return std::forward<Func>(func)();
#endif
}

template <typename Func>
decltype(auto) MeasurePresentationPhase(PresentationProfilePhase phase, Func&& func) {
#ifdef OPENSW_PROFILE
    const auto start = std::chrono::steady_clock::now();
    if constexpr (std::is_void_v<std::invoke_result_t<Func>>) {
        std::forward<Func>(func)();
        const auto elapsed = std::chrono::steady_clock::now() - start;
        ProfilePresentationPhase(
            phase, static_cast<u64>(
                       std::chrono::duration_cast<std::chrono::nanoseconds>(elapsed).count()));
    } else {
        auto result = std::forward<Func>(func)();
        const auto elapsed = std::chrono::steady_clock::now() - start;
        ProfilePresentationPhase(
            phase, static_cast<u64>(
                       std::chrono::duration_cast<std::chrono::nanoseconds>(elapsed).count()));
        return result;
    }
#else
    return std::forward<Func>(func)();
#endif
}

} // namespace Vulkan
