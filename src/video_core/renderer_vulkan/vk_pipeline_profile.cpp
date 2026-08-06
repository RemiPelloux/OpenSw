// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include "video_core/renderer_vulkan/vk_pipeline_profile.h"

#ifdef OPENSW_PROFILE

#include <atomic>

#include "common/logging.h"

namespace Vulkan {
namespace {

struct PipelineProfileCounters {
    std::atomic<u64> title_id{};
    std::atomic<u64> cache_hits{};
    std::atomic<u64> cache_misses{};
    std::atomic<u64> compilations{};
    std::atomic<u64> max_queue_depth{};
    std::atomic<u64> small_draw_waits{};
    std::atomic<u64> pipeline_waits{};
    std::atomic<u64> pipeline_wait_ns{};
    std::atomic<u64> translation_ns{};
    std::atomic<u64> spirv_ns{};
    std::atomic<u64> shader_module_ns{};
    std::atomic<u64> vulkan_pipeline_ns{};
};

PipelineProfileCounters counters;

void Add(std::atomic<u64>& counter, u64 value = 1) {
    counter.fetch_add(value, std::memory_order_relaxed);
}

} // namespace

void ProfilePipelineCacheHit() {
    Add(counters.cache_hits);
}

void ProfilePipelineCacheMiss() {
    Add(counters.cache_misses);
}

void ProfilePipelineCompilation() {
    Add(counters.compilations);
}

void ProfilePipelineQueueDepth(size_t depth) {
    u64 observed = counters.max_queue_depth.load(std::memory_order_relaxed);
    while (observed < depth && !counters.max_queue_depth.compare_exchange_weak(
                                   observed, depth, std::memory_order_relaxed)) {
    }
}

void ProfileSmallDrawWait() {
    Add(counters.small_draw_waits);
}

void ProfilePipelineWait(u64 nanoseconds) {
    Add(counters.pipeline_waits);
    Add(counters.pipeline_wait_ns, nanoseconds);
}

void ProfilePipelinePhase(PipelineProfilePhase phase, u64 nanoseconds) {
    switch (phase) {
    case PipelineProfilePhase::MaxwellTranslation:
        Add(counters.translation_ns, nanoseconds);
        break;
    case PipelineProfilePhase::SpirvEmission:
        Add(counters.spirv_ns, nanoseconds);
        break;
    case PipelineProfilePhase::ShaderModule:
        Add(counters.shader_module_ns, nanoseconds);
        break;
    case PipelineProfilePhase::VulkanPipeline:
        Add(counters.vulkan_pipeline_ns, nanoseconds);
        break;
    }
}

void ResetPipelineProfile(u64 title_id) {
    counters.title_id.store(0, std::memory_order_release);
    counters.cache_hits.store(0, std::memory_order_relaxed);
    counters.cache_misses.store(0, std::memory_order_relaxed);
    counters.compilations.store(0, std::memory_order_relaxed);
    counters.max_queue_depth.store(0, std::memory_order_relaxed);
    counters.small_draw_waits.store(0, std::memory_order_relaxed);
    counters.pipeline_waits.store(0, std::memory_order_relaxed);
    counters.pipeline_wait_ns.store(0, std::memory_order_relaxed);
    counters.translation_ns.store(0, std::memory_order_relaxed);
    counters.spirv_ns.store(0, std::memory_order_relaxed);
    counters.shader_module_ns.store(0, std::memory_order_relaxed);
    counters.vulkan_pipeline_ns.store(0, std::memory_order_relaxed);
    counters.title_id.store(title_id, std::memory_order_release);
}

PipelineProfileSnapshot GetPipelineProfileSnapshot() {
    const u64 title_id = counters.title_id.load(std::memory_order_acquire);
    if (title_id == 0) {
        return {};
    }
    const PipelineProfileSnapshot snapshot{
        title_id,
        counters.cache_hits.load(std::memory_order_relaxed),
        counters.cache_misses.load(std::memory_order_relaxed),
        counters.compilations.load(std::memory_order_relaxed),
        counters.max_queue_depth.load(std::memory_order_relaxed),
        counters.small_draw_waits.load(std::memory_order_relaxed),
        counters.pipeline_waits.load(std::memory_order_relaxed),
        counters.pipeline_wait_ns.load(std::memory_order_relaxed),
        counters.translation_ns.load(std::memory_order_relaxed),
        counters.spirv_ns.load(std::memory_order_relaxed),
        counters.shader_module_ns.load(std::memory_order_relaxed),
        counters.vulkan_pipeline_ns.load(std::memory_order_relaxed),
    };
    if (counters.title_id.load(std::memory_order_acquire) != title_id) {
        return {};
    }
    return snapshot;
}

void ReportPipelineProfile() {
    LOG_INFO(Render_Vulkan,
             "OpenSw pipeline profile: title_id={:016X} hits={} misses={} compilations={} max_queue={} "
             "small_draw_waits={} waits={} wait_ns={} translate_ns={} spirv_ns={} "
             "module_ns={} vulkan_pipeline_ns={}",
             counters.title_id.load(std::memory_order_relaxed),
             counters.cache_hits.load(std::memory_order_relaxed),
             counters.cache_misses.load(std::memory_order_relaxed),
             counters.compilations.load(std::memory_order_relaxed),
             counters.max_queue_depth.load(std::memory_order_relaxed),
             counters.small_draw_waits.load(std::memory_order_relaxed),
             counters.pipeline_waits.load(std::memory_order_relaxed),
             counters.pipeline_wait_ns.load(std::memory_order_relaxed),
             counters.translation_ns.load(std::memory_order_relaxed),
             counters.spirv_ns.load(std::memory_order_relaxed),
             counters.shader_module_ns.load(std::memory_order_relaxed),
             counters.vulkan_pipeline_ns.load(std::memory_order_relaxed));
}

} // namespace Vulkan

#endif
