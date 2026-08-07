// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#pragma once

#include <algorithm>
#include <array>
#include <cstring>
#include <span>
#include <type_traits>
#include <utility>
#include <vector>

#include "common/common_types.h"

namespace Vulkan {

// A small scan-resistant cache for hot descriptor payloads. It starts with one entry and uses
// ghost fingerprints to grow only when an evicted payload is requested again.
template <typename Payload, typename Value, size_t MaxEntries = 4, size_t AdaptationWindow = 512>
class AdaptivePayloadCache {
    static_assert(MaxEntries > 1);
    static_assert(AdaptationWindow > 0);
    static_assert(std::is_trivially_copyable_v<Payload>);

public:
    struct LookupResult {
        Value value{};
        u64 fingerprint{};
        size_t depth{};
        bool hit{};
        bool grew{};
        bool shrank{};
    };

    template <typename Fingerprint>
    [[nodiscard]] LookupResult Lookup(std::span<const Payload> payload, u64 generation,
                                      Fingerprint&& make_fingerprint) {
        EnsureGeneration(generation);
        const bool shrank = Adapt();
        ++window_lookups;

        if (size != 0 && Equal(entries[0], payload)) {
            return {.value = entries[0].value, .depth = 1, .hit = true, .shrank = shrank};
        }

        const u64 fingerprint = std::forward<Fingerprint>(make_fingerprint)();
        for (size_t index = 1; index < size; ++index) {
            if (entries[index].fingerprint != fingerprint || !Equal(entries[index], payload)) {
                continue;
            }
            ++window_deep_hits;
            Entry hit = std::move(entries[index]);
            std::move_backward(entries.begin(), entries.begin() + index,
                               entries.begin() + index + 1);
            entries[0] = std::move(hit);
            return {
                .value = entries[0].value,
                .fingerprint = fingerprint,
                .depth = index + 1,
                .hit = true,
                .shrank = shrank,
            };
        }

        bool grew = false;
        if (capacity < MaxEntries && IsGhost(fingerprint)) {
            ++capacity;
            grew = true;
        }
        return {
            .fingerprint = fingerprint,
            .grew = grew,
            .shrank = shrank,
        };
    }

    void Insert(std::span<const Payload> payload, u64 generation, u64 fingerprint, Value value) {
        EnsureGeneration(generation);
        size_t target{};
        if (size < capacity) {
            target = size++;
        } else {
            target = size - 1;
            RememberGhost(entries[target].fingerprint);
        }
        if (target != 0) {
            Entry recycled = std::move(entries[target]);
            std::move_backward(entries.begin(), entries.begin() + target,
                               entries.begin() + target + 1);
            entries[0] = std::move(recycled);
        }
        entries[0].payload.assign(payload.begin(), payload.end());
        entries[0].value = value;
        entries[0].fingerprint = fingerprint;
    }

    [[nodiscard]] size_t Capacity() const noexcept {
        return capacity;
    }

    [[nodiscard]] size_t Size() const noexcept {
        return size;
    }

private:
    struct Entry {
        std::vector<Payload> payload;
        Value value{};
        u64 fingerprint{};
    };

    [[nodiscard]] static bool Equal(const Entry& entry, std::span<const Payload> payload) {
        return entry.payload.size() == payload.size() &&
               (payload.empty() ||
                std::memcmp(entry.payload.data(), payload.data(), payload.size_bytes()) == 0);
    }

    void EnsureGeneration(u64 generation) {
        if (current_generation == generation) {
            return;
        }
        current_generation = generation;
        size = 0;
    }

    [[nodiscard]] bool Adapt() {
        if (window_lookups < AdaptationWindow) {
            return false;
        }
        const bool should_shrink = capacity > 1 && window_deep_hits * 64 < window_lookups;
        window_lookups = 0;
        window_deep_hits = 0;
        if (!should_shrink) {
            return false;
        }
        --capacity;
        while (size > capacity) {
            --size;
            RememberGhost(entries[size].fingerprint);
        }
        return true;
    }

    void RememberGhost(u64 fingerprint) {
        ghosts[ghost_cursor] = fingerprint;
        ghost_cursor = (ghost_cursor + 1) % ghosts.size();
        ghost_size = std::min(ghost_size + 1, ghosts.size());
    }

    [[nodiscard]] bool IsGhost(u64 fingerprint) const {
        return std::ranges::find(ghosts.begin(), ghosts.begin() + ghost_size, fingerprint) !=
               ghosts.begin() + ghost_size;
    }

    std::array<Entry, MaxEntries> entries;
    std::array<u64, MaxEntries> ghosts{};
    size_t size{};
    size_t capacity{1};
    size_t ghost_cursor{};
    size_t ghost_size{};
    size_t window_lookups{};
    size_t window_deep_hits{};
    u64 current_generation{};
};

} // namespace Vulkan
