// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include "video_core/renderer_vulkan/vk_pipeline_profile.h"

#include <atomic>

namespace Vulkan {
namespace {

struct RenderRuntimeCounters {
    std::atomic<u64> title_id{};
    std::atomic<int> requested_workers{};
    std::atomic<u64> effective_workers{1};
    std::atomic<PipelineWorkerReason> worker_reason{PipelineWorkerReason::Auto};
    std::atomic<bool> async_shaders{};
    std::atomic<bool> async_gpu{};
    std::atomic<bool> async_presentation{};
    std::atomic<bool> descriptor_buffer_available{};
    std::atomic<int> presentation_target{};
};

RenderRuntimeCounters runtime;

} // namespace

void SetRenderRuntimeSnapshot(PipelineWorkerResolution workers, bool async_shaders, bool async_gpu,
                              bool async_presentation, bool descriptor_buffer_available,
                              int presentation_target) {
    runtime.requested_workers.store(workers.requested, std::memory_order_relaxed);
    runtime.effective_workers.store(workers.effective, std::memory_order_relaxed);
    runtime.worker_reason.store(workers.reason, std::memory_order_relaxed);
    runtime.async_shaders.store(async_shaders, std::memory_order_relaxed);
    runtime.async_gpu.store(async_gpu, std::memory_order_relaxed);
    runtime.async_presentation.store(async_presentation, std::memory_order_relaxed);
    runtime.descriptor_buffer_available.store(descriptor_buffer_available, std::memory_order_relaxed);
    runtime.presentation_target.store(presentation_target, std::memory_order_relaxed);
}

RenderRuntimeSnapshot GetRenderRuntimeSnapshot() {
    return {
        RenderRuntimeSnapshotSchemaVersion,
        runtime.title_id.load(std::memory_order_acquire),
        static_cast<u64>(static_cast<s64>(runtime.requested_workers.load(std::memory_order_relaxed))),
        runtime.effective_workers.load(std::memory_order_relaxed),
        static_cast<u64>(runtime.worker_reason.load(std::memory_order_relaxed)),
        runtime.async_shaders.load(std::memory_order_relaxed),
        runtime.async_gpu.load(std::memory_order_relaxed),
        runtime.async_presentation.load(std::memory_order_relaxed),
        runtime.descriptor_buffer_available.load(std::memory_order_relaxed),
        static_cast<u64>(static_cast<s64>(runtime.presentation_target.load(std::memory_order_relaxed))),
    };
}

} // namespace Vulkan

#ifdef OPENSW_PROFILE

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
    std::atomic<u64> max_present_queue_depth{};
    std::atomic<u64> free_frame_wait_ns{};
    std::atomic<u64> scheduler_wait_ns{};
    std::atomic<u64> swapchain_acquire_ns{};
    std::atomic<u64> present_ns{};
    std::atomic<bool> window_active{};
    std::atomic<u64> window_max_queue_depth{};
    std::atomic<u64> window_max_present_queue_depth{};
    std::atomic<u64> window_small_draw_waits{};
    std::atomic<u64> window_small_draw_wait_ns{};
    std::atomic<u64> self_hits{};
    std::atomic<u64> transition_hits{};
    std::atomic<u64> transition_probes{};
    std::atomic<u64> transition_hash_hits{};
    std::atomic<u64> slow_path_lookups{};
    std::atomic<u64> descriptor_buffer_draws{};
    std::atomic<u64> push_descriptor_draws{};
    std::atomic<u64> descriptor_set_draws{};
    std::atomic<u64> descriptor_payload_reuses{};
    std::atomic<u64> descriptor_bytes_written{};
    std::atomic<u64> descriptor_chunk_switches{};
    std::atomic<u64> descriptor_ring_stalls{};
    std::atomic<u64> descriptor_ring_stall_ns{};
    std::atomic<u64> vertex_buffer_bind_calls{};
    std::atomic<u64> vertex_buffer_slots_bound{};
    std::atomic<u64> vertex_buffer_synchronized_bytes{};
    std::atomic<u64> vertex_buffer_uploaded_bytes{};
    std::atomic<u64> texture_upload_bytes{};
    std::atomic<u64> texture_upload_ns{};
    std::atomic<u64> texture_decode_bytes{};
    std::atomic<u64> texture_decode_ns{};
    std::atomic<u64> texture_unswizzle_bytes{};
    std::atomic<u64> texture_unswizzle_ns{};
    std::atomic<u64> descriptor_offset_calls{};
    std::atomic<u64> descriptor_offset_skips{};
    std::atomic<u64> adaptive_descriptor_lookups{};
    std::atomic<u64> adaptive_descriptor_hot_hits{};
    std::atomic<u64> adaptive_descriptor_deep_hits{};
    std::atomic<u64> adaptive_descriptor_grows{};
    std::atomic<u64> adaptive_descriptor_shrinks{};
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
    if (counters.window_active.load(std::memory_order_relaxed)) {
        observed = counters.window_max_queue_depth.load(std::memory_order_relaxed);
        while (observed < depth && !counters.window_max_queue_depth.compare_exchange_weak(
                                       observed, depth, std::memory_order_relaxed)) {
        }
    }
}

void ProfileSmallDrawWait() {
    Add(counters.small_draw_waits);
}

void ProfilePipelineWait(u64 nanoseconds, bool small_draw) {
    Add(counters.pipeline_waits);
    Add(counters.pipeline_wait_ns, nanoseconds);
    if (small_draw) {
        if (counters.window_active.load(std::memory_order_relaxed)) {
            Add(counters.window_small_draw_waits);
            Add(counters.window_small_draw_wait_ns, nanoseconds);
        }
    }
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

void ProfilePresentationQueueDepth(size_t depth) {
    u64 observed = counters.max_present_queue_depth.load(std::memory_order_relaxed);
    while (observed < depth && !counters.max_present_queue_depth.compare_exchange_weak(
                                   observed, depth, std::memory_order_relaxed)) {
    }
    if (counters.window_active.load(std::memory_order_relaxed)) {
        observed = counters.window_max_present_queue_depth.load(std::memory_order_relaxed);
        while (observed < depth && !counters.window_max_present_queue_depth.compare_exchange_weak(
                                       observed, depth, std::memory_order_relaxed)) {
        }
    }
}

void ProfilePresentationPhase(PresentationProfilePhase phase, u64 nanoseconds) {
    switch (phase) {
    case PresentationProfilePhase::FreeFrameWait:
        Add(counters.free_frame_wait_ns, nanoseconds);
        break;
    case PresentationProfilePhase::SchedulerWait:
        Add(counters.scheduler_wait_ns, nanoseconds);
        break;
    case PresentationProfilePhase::SwapchainAcquire:
        Add(counters.swapchain_acquire_ns, nanoseconds);
        break;
    case PresentationProfilePhase::Present:
        Add(counters.present_ns, nanoseconds);
        break;
    }
}

void ProfilePipelineSelfHit() { Add(counters.self_hits); }
void ProfilePipelineTransitionLookup(size_t probes, bool hashed, bool hit) {
    if (hit) Add(counters.transition_hits);
    Add(counters.transition_probes, probes);
    if (hashed && hit) Add(counters.transition_hash_hits);
}
void ProfilePipelineSlowPath() { Add(counters.slow_path_lookups); }
void ProfileDescriptorDraw(bool descriptor_buffer, bool push_descriptor) {
    Add(descriptor_buffer ? counters.descriptor_buffer_draws
                          : push_descriptor ? counters.push_descriptor_draws
                                            : counters.descriptor_set_draws);
}
void ProfileDescriptorPayloadReuse() { Add(counters.descriptor_payload_reuses); }
void ProfileDescriptorBytesWritten(u64 bytes) { Add(counters.descriptor_bytes_written, bytes); }
void ProfileDescriptorChunkSwitch() { Add(counters.descriptor_chunk_switches); }
void ProfileDescriptorRingStall(u64 nanoseconds) {
    Add(counters.descriptor_ring_stalls);
    Add(counters.descriptor_ring_stall_ns, nanoseconds);
}
void ProfileDescriptorOffset(bool emitted) {
    Add(emitted ? counters.descriptor_offset_calls : counters.descriptor_offset_skips);
}
void ProfileAdaptiveDescriptorCache(bool hit, size_t depth, bool grew, bool shrank) {
    Add(counters.adaptive_descriptor_lookups);
    if (hit) {
        Add(depth == 1 ? counters.adaptive_descriptor_hot_hits
                       : counters.adaptive_descriptor_deep_hits);
    }
    if (grew) Add(counters.adaptive_descriptor_grows);
    if (shrank) Add(counters.adaptive_descriptor_shrinks);
}
void ProfileVertexBufferBind(size_t slots) {
    Add(counters.vertex_buffer_bind_calls);
    Add(counters.vertex_buffer_slots_bound, slots);
}
void ProfileVertexBufferSynchronized(u64 synchronized_bytes, u64 uploaded_bytes) {
    Add(counters.vertex_buffer_synchronized_bytes, synchronized_bytes);
    Add(counters.vertex_buffer_uploaded_bytes, uploaded_bytes);
}
void ProfileTextureUpload(u64 bytes, u64 nanoseconds) {
    Add(counters.texture_upload_bytes, bytes);
    Add(counters.texture_upload_ns, nanoseconds);
}
void ProfileTextureDecode(u64 bytes, u64 nanoseconds) {
    Add(counters.texture_decode_bytes, bytes);
    Add(counters.texture_decode_ns, nanoseconds);
}
void ProfileTextureUnswizzle(u64 bytes, u64 nanoseconds) {
    Add(counters.texture_unswizzle_bytes, bytes);
    Add(counters.texture_unswizzle_ns, nanoseconds);
}

void ResetPipelineProfile(u64 title_id) {
    runtime.title_id.store(title_id, std::memory_order_release);
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
    counters.max_present_queue_depth.store(0, std::memory_order_relaxed);
    counters.free_frame_wait_ns.store(0, std::memory_order_relaxed);
    counters.scheduler_wait_ns.store(0, std::memory_order_relaxed);
    counters.swapchain_acquire_ns.store(0, std::memory_order_relaxed);
    counters.present_ns.store(0, std::memory_order_relaxed);
    counters.window_active.store(false, std::memory_order_relaxed);
    counters.window_max_queue_depth.store(0, std::memory_order_relaxed);
    counters.window_max_present_queue_depth.store(0, std::memory_order_relaxed);
    counters.window_small_draw_waits.store(0, std::memory_order_relaxed);
    counters.window_small_draw_wait_ns.store(0, std::memory_order_relaxed);
    counters.self_hits.store(0, std::memory_order_relaxed);
    counters.transition_hits.store(0, std::memory_order_relaxed);
    counters.transition_probes.store(0, std::memory_order_relaxed);
    counters.transition_hash_hits.store(0, std::memory_order_relaxed);
    counters.slow_path_lookups.store(0, std::memory_order_relaxed);
    counters.descriptor_buffer_draws.store(0, std::memory_order_relaxed);
    counters.push_descriptor_draws.store(0, std::memory_order_relaxed);
    counters.descriptor_set_draws.store(0, std::memory_order_relaxed);
    counters.descriptor_payload_reuses.store(0, std::memory_order_relaxed);
    counters.descriptor_bytes_written.store(0, std::memory_order_relaxed);
    counters.descriptor_chunk_switches.store(0, std::memory_order_relaxed);
    counters.descriptor_ring_stalls.store(0, std::memory_order_relaxed);
    counters.descriptor_ring_stall_ns.store(0, std::memory_order_relaxed);
    counters.vertex_buffer_bind_calls.store(0, std::memory_order_relaxed);
    counters.vertex_buffer_slots_bound.store(0, std::memory_order_relaxed);
    counters.vertex_buffer_synchronized_bytes.store(0, std::memory_order_relaxed);
    counters.vertex_buffer_uploaded_bytes.store(0, std::memory_order_relaxed);
    counters.texture_upload_bytes.store(0, std::memory_order_relaxed);
    counters.texture_upload_ns.store(0, std::memory_order_relaxed);
    counters.texture_decode_bytes.store(0, std::memory_order_relaxed);
    counters.texture_decode_ns.store(0, std::memory_order_relaxed);
    counters.texture_unswizzle_bytes.store(0, std::memory_order_relaxed);
    counters.texture_unswizzle_ns.store(0, std::memory_order_relaxed);
    counters.descriptor_offset_calls.store(0, std::memory_order_relaxed);
    counters.descriptor_offset_skips.store(0, std::memory_order_relaxed);
    counters.adaptive_descriptor_lookups.store(0, std::memory_order_relaxed);
    counters.adaptive_descriptor_hot_hits.store(0, std::memory_order_relaxed);
    counters.adaptive_descriptor_deep_hits.store(0, std::memory_order_relaxed);
    counters.adaptive_descriptor_grows.store(0, std::memory_order_relaxed);
    counters.adaptive_descriptor_shrinks.store(0, std::memory_order_relaxed);
    counters.title_id.store(title_id, std::memory_order_release);
}

void StartPipelineProfileWindow() {
    counters.window_active.store(false, std::memory_order_release);
    counters.window_max_queue_depth.store(0, std::memory_order_relaxed);
    counters.window_max_present_queue_depth.store(0, std::memory_order_relaxed);
    counters.window_small_draw_waits.store(0, std::memory_order_relaxed);
    counters.window_small_draw_wait_ns.store(0, std::memory_order_relaxed);
    counters.window_active.store(true, std::memory_order_release);
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
        counters.max_present_queue_depth.load(std::memory_order_relaxed),
        counters.free_frame_wait_ns.load(std::memory_order_relaxed),
        counters.scheduler_wait_ns.load(std::memory_order_relaxed),
        counters.swapchain_acquire_ns.load(std::memory_order_relaxed),
        counters.present_ns.load(std::memory_order_relaxed),
        counters.window_max_queue_depth.load(std::memory_order_relaxed),
        counters.window_max_present_queue_depth.load(std::memory_order_relaxed),
        counters.window_small_draw_waits.load(std::memory_order_relaxed),
        counters.window_small_draw_wait_ns.load(std::memory_order_relaxed),
        counters.self_hits.load(std::memory_order_relaxed),
        counters.transition_hits.load(std::memory_order_relaxed),
        counters.transition_probes.load(std::memory_order_relaxed),
        counters.transition_hash_hits.load(std::memory_order_relaxed),
        counters.slow_path_lookups.load(std::memory_order_relaxed),
        counters.descriptor_buffer_draws.load(std::memory_order_relaxed),
        counters.push_descriptor_draws.load(std::memory_order_relaxed),
        counters.descriptor_set_draws.load(std::memory_order_relaxed),
        counters.descriptor_payload_reuses.load(std::memory_order_relaxed),
        counters.descriptor_bytes_written.load(std::memory_order_relaxed),
        counters.descriptor_chunk_switches.load(std::memory_order_relaxed),
        counters.descriptor_ring_stalls.load(std::memory_order_relaxed),
        counters.descriptor_ring_stall_ns.load(std::memory_order_relaxed),
        counters.vertex_buffer_bind_calls.load(std::memory_order_relaxed),
        counters.vertex_buffer_slots_bound.load(std::memory_order_relaxed),
        counters.vertex_buffer_synchronized_bytes.load(std::memory_order_relaxed),
        counters.vertex_buffer_uploaded_bytes.load(std::memory_order_relaxed),
        counters.texture_upload_bytes.load(std::memory_order_relaxed),
        counters.texture_upload_ns.load(std::memory_order_relaxed),
        counters.texture_decode_bytes.load(std::memory_order_relaxed),
        counters.texture_decode_ns.load(std::memory_order_relaxed),
        counters.texture_unswizzle_bytes.load(std::memory_order_relaxed),
        counters.texture_unswizzle_ns.load(std::memory_order_relaxed),
        counters.descriptor_offset_calls.load(std::memory_order_relaxed),
        counters.descriptor_offset_skips.load(std::memory_order_relaxed),
        counters.adaptive_descriptor_lookups.load(std::memory_order_relaxed),
        counters.adaptive_descriptor_hot_hits.load(std::memory_order_relaxed),
        counters.adaptive_descriptor_deep_hits.load(std::memory_order_relaxed),
        counters.adaptive_descriptor_grows.load(std::memory_order_relaxed),
        counters.adaptive_descriptor_shrinks.load(std::memory_order_relaxed),
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
             "module_ns={} vulkan_pipeline_ns={} present_max_queue={} free_frame_wait_ns={} "
             "scheduler_wait_ns={} swapchain_acquire_ns={} present_ns={}",
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
             counters.vulkan_pipeline_ns.load(std::memory_order_relaxed),
             counters.max_present_queue_depth.load(std::memory_order_relaxed),
             counters.free_frame_wait_ns.load(std::memory_order_relaxed),
             counters.scheduler_wait_ns.load(std::memory_order_relaxed),
             counters.swapchain_acquire_ns.load(std::memory_order_relaxed),
             counters.present_ns.load(std::memory_order_relaxed));
}

} // namespace Vulkan

#endif
