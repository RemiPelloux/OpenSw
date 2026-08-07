// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

#pragma once

#include <array>
#ifdef OPENSW_PROFILE
#include <chrono>
#ifdef __ANDROID__
#include <android/trace.h>
#endif
#endif
#include <cstddef>
#include <type_traits>
#include <utility>

#include "common/common_types.h"
#include "video_core/renderer_vulkan/vk_pipeline_workers.h"

namespace Vulkan {

class ProfileTraceScope {
public:
    explicit ProfileTraceScope(const char* name) {
#if defined(OPENSW_PROFILE) && defined(__ANDROID__)
        ATrace_beginSection(name);
#else
        (void)name;
#endif
    }

    ~ProfileTraceScope() {
#if defined(OPENSW_PROFILE) && defined(__ANDROID__)
        ATrace_endSection();
#endif
    }
};

class ProfileTimer {
public:
    ProfileTimer() {
#ifdef OPENSW_PROFILE
        start = std::chrono::steady_clock::now();
#endif
    }

    [[nodiscard]] u64 ElapsedNs() const {
#ifdef OPENSW_PROFILE
        return static_cast<u64>(std::chrono::duration_cast<std::chrono::nanoseconds>(
                                    std::chrono::steady_clock::now() - start)
                                    .count());
#else
        return 0;
#endif
    }

private:
#ifdef OPENSW_PROFILE
    std::chrono::steady_clock::time_point start;
#endif
};

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

constexpr const char* PipelineProfileTraceName(PipelineProfilePhase phase) {
    switch (phase) {
    case PipelineProfilePhase::MaxwellTranslation:
        return "OpenSw Maxwell translation";
    case PipelineProfilePhase::SpirvEmission:
        return "OpenSw SPIR-V emission";
    case PipelineProfilePhase::ShaderModule:
        return "OpenSw shader module";
    case PipelineProfilePhase::VulkanPipeline:
        return "OpenSw Vulkan pipeline creation";
    }
    return "OpenSw pipeline";
}

constexpr const char* PresentationProfileTraceName(PresentationProfilePhase phase) {
    switch (phase) {
    case PresentationProfilePhase::FreeFrameWait:
        return "OpenSw free-frame wait";
    case PresentationProfilePhase::SchedulerWait:
        return "OpenSw scheduler wait";
    case PresentationProfilePhase::SwapchainAcquire:
        return "OpenSw acquire";
    case PresentationProfilePhase::Present:
        return "OpenSw present";
    }
    return "OpenSw presentation";
}

// The first 17 fields are the original session snapshot contract. New fields are append-only.
constexpr size_t PipelineProfileSnapshotSize = 46;
using PipelineProfileSnapshot = std::array<u64, PipelineProfileSnapshotSize>;

constexpr u64 RenderRuntimeSnapshotSchemaVersion = 1;
constexpr size_t RenderRuntimeSnapshotSize = 10;
using RenderRuntimeSnapshot = std::array<u64, RenderRuntimeSnapshotSize>;

void SetRenderRuntimeSnapshot(PipelineWorkerResolution workers, bool async_shaders, bool async_gpu,
                              bool async_presentation, bool descriptor_buffer_available,
                              int presentation_target);
RenderRuntimeSnapshot GetRenderRuntimeSnapshot();

#ifdef OPENSW_PROFILE
void ProfilePipelineCacheHit();
void ProfilePipelineCacheMiss();
void ProfilePipelineCompilation();
void ProfilePipelineQueueDepth(size_t depth);
void ProfileSmallDrawWait();
void ProfilePipelineWait(u64 nanoseconds, bool small_draw = false);
void ProfilePipelinePhase(PipelineProfilePhase phase, u64 nanoseconds);
void ProfilePresentationQueueDepth(size_t depth);
void ProfilePresentationPhase(PresentationProfilePhase phase, u64 nanoseconds);
void ProfilePipelineSelfHit();
void ProfilePipelineTransitionLookup(size_t probes, bool hashed, bool hit);
void ProfilePipelineSlowPath();
void ProfileDescriptorDraw(bool descriptor_buffer, bool push_descriptor);
void ProfileDescriptorPayloadReuse();
void ProfileDescriptorBytesWritten(u64 bytes);
void ProfileDescriptorChunkSwitch();
void ProfileDescriptorRingStall(u64 nanoseconds);
void ProfileDescriptorOffset(bool emitted);
void ProfileVertexBufferBind(size_t slots);
void ProfileVertexBufferSynchronized(u64 synchronized_bytes, u64 uploaded_bytes);
void ProfileTextureUpload(u64 bytes, u64 nanoseconds);
void ProfileTextureDecode(u64 bytes, u64 nanoseconds);
void ProfileTextureUnswizzle(u64 bytes, u64 nanoseconds);
void ResetPipelineProfile(u64 title_id);
void StartPipelineProfileWindow();
void ReportPipelineProfile();
PipelineProfileSnapshot GetPipelineProfileSnapshot();
#else
inline void ProfilePipelineCacheHit() {}
inline void ProfilePipelineCacheMiss() {}
inline void ProfilePipelineCompilation() {}
inline void ProfilePipelineQueueDepth(size_t) {}
inline void ProfileSmallDrawWait() {}
inline void ProfilePipelineWait(u64, bool = false) {}
inline void ProfilePipelinePhase(PipelineProfilePhase, u64) {}
inline void ProfilePresentationQueueDepth(size_t) {}
inline void ProfilePresentationPhase(PresentationProfilePhase, u64) {}
inline void ProfilePipelineSelfHit() {}
inline void ProfilePipelineTransitionLookup(size_t, bool, bool) {}
inline void ProfilePipelineSlowPath() {}
inline void ProfileDescriptorDraw(bool, bool) {}
inline void ProfileDescriptorPayloadReuse() {}
inline void ProfileDescriptorBytesWritten(u64) {}
inline void ProfileDescriptorChunkSwitch() {}
inline void ProfileDescriptorRingStall(u64) {}
inline void ProfileDescriptorOffset(bool) {}
inline void ProfileVertexBufferBind(size_t) {}
inline void ProfileVertexBufferSynchronized(u64, u64) {}
inline void ProfileTextureUpload(u64, u64) {}
inline void ProfileTextureDecode(u64, u64) {}
inline void ProfileTextureUnswizzle(u64, u64) {}
inline void ResetPipelineProfile(u64) {}
inline void StartPipelineProfileWindow() {}
inline void ReportPipelineProfile() {}
inline PipelineProfileSnapshot GetPipelineProfileSnapshot() {
    return {};
}
#endif

template <typename Func>
auto MeasurePipelinePhase(PipelineProfilePhase phase, Func&& func) {
#ifdef OPENSW_PROFILE
    ProfileTraceScope trace{PipelineProfileTraceName(phase)};
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
    ProfileTraceScope trace{PresentationProfileTraceName(phase)};
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
