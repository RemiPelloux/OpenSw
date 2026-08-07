// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#include <array>
#include <span>

#include <catch2/catch_test_macros.hpp>

#include "video_core/renderer_vulkan/vk_adaptive_payload_cache.h"

namespace {

using Cache = Vulkan::AdaptivePayloadCache<int, int>;

Cache::LookupResult Lookup(Cache& cache, std::span<const int> payload, u64 generation) {
    return cache.Lookup(payload, generation, [payload] {
        u64 hash = 1469598103934665603ULL;
        for (const int value : payload) {
            hash = (hash ^ static_cast<u64>(value)) * 1099511628211ULL;
        }
        return hash;
    });
}

void Insert(Cache& cache, std::span<const int> payload, u64 generation, int value) {
    const auto lookup = Lookup(cache, payload, generation);
    REQUIRE_FALSE(lookup.hit);
    cache.Insert(payload, generation, lookup.fingerprint, value);
}

} // namespace

TEST_CASE("Adaptive payload cache keeps the hot path at one entry", "[video_core][vulkan]") {
    Cache cache;
    const std::array payload{1, 2, 3};
    Insert(cache, payload, 1, 7);

    int hash_calls{};
    const auto hit = cache.Lookup(payload, 1, [&] {
        ++hash_calls;
        return 1ULL;
    });
    CHECK(hit.hit);
    CHECK(hit.depth == 1);
    CHECK(hit.value == 7);
    CHECK(hash_calls == 0);
    CHECK(cache.Capacity() == 1);
}

TEST_CASE("Adaptive payload cache learns alternating working sets", "[video_core][vulkan]") {
    Cache cache;
    const std::array first{1};
    const std::array second{2};
    Insert(cache, first, 1, 10);
    Insert(cache, second, 1, 20);

    const auto repeated = Lookup(cache, first, 1);
    REQUIRE_FALSE(repeated.hit);
    CHECK(repeated.grew);
    CHECK(cache.Capacity() == 2);
    cache.Insert(first, 1, repeated.fingerprint, 10);

    const auto deep_hit = Lookup(cache, second, 1);
    CHECK(deep_hit.hit);
    CHECK(deep_hit.depth == 2);
    CHECK(deep_hit.value == 20);
}

TEST_CASE("Adaptive payload cache resists one-time scans", "[video_core][vulkan]") {
    Cache cache;
    for (int value = 1; value <= 32; ++value) {
        const std::array payload{value};
        Insert(cache, payload, 1, value);
    }
    CHECK(cache.Capacity() == 1);
    CHECK(cache.Size() == 1);
}

TEST_CASE("Adaptive payload cache invalidates locations between ring generations",
          "[video_core][vulkan]") {
    Cache cache;
    const std::array payload{4, 5};
    Insert(cache, payload, 1, 45);
    CHECK(Lookup(cache, payload, 1).hit);
    CHECK_FALSE(Lookup(cache, payload, 2).hit);
    CHECK(cache.Size() == 0);
}

TEST_CASE("Adaptive payload cache verifies payloads after fingerprint collisions",
          "[video_core][vulkan]") {
    Cache cache;
    const std::array first{1};
    const std::array second{2};
    const std::array third{3};
    auto lookup = [&](std::span<const int> payload) {
        return cache.Lookup(payload, 1, [] { return 7ULL; });
    };

    auto miss = lookup(first);
    cache.Insert(first, 1, miss.fingerprint, 10);
    miss = lookup(second);
    cache.Insert(second, 1, miss.fingerprint, 20);
    miss = lookup(first);
    REQUIRE(miss.grew);
    cache.Insert(first, 1, miss.fingerprint, 10);

    const auto collision = lookup(third);
    CHECK_FALSE(collision.hit);
}

TEST_CASE("Adaptive payload cache shrinks after deep reuse disappears", "[video_core][vulkan]") {
    using FastCache = Vulkan::AdaptivePayloadCache<int, int, 4, 8>;
    FastCache cache;
    const std::array first{1};
    const std::array second{2};
    auto lookup = [&](std::span<const int> payload) {
        return cache.Lookup(payload, 1, [payload] { return static_cast<u64>(payload.front()); });
    };

    auto miss = lookup(first);
    cache.Insert(first, 1, miss.fingerprint, 1);
    miss = lookup(second);
    cache.Insert(second, 1, miss.fingerprint, 2);
    miss = lookup(first);
    REQUIRE(miss.grew);
    cache.Insert(first, 1, miss.fingerprint, 1);
    REQUIRE(cache.Capacity() == 2);

    bool shrank = false;
    for (int repeat = 0; repeat < 16; ++repeat) {
        const auto result = lookup(first);
        CHECK(result.hit);
        shrank = shrank || result.shrank;
    }
    CHECK(shrank);
    CHECK(cache.Capacity() == 1);
    CHECK(cache.Size() == 1);
}
