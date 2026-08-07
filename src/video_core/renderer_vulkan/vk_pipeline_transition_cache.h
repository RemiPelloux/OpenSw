// SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
// SPDX-License-Identifier: GPL-3.0-or-later

#pragma once

#include <algorithm>
#include <cstddef>
#include <functional>
#include <utility>
#include <vector>

#include <ankerl/unordered_dense.h>

namespace Vulkan {

template <typename Key, typename Value, typename Hash = std::hash<Key>>
class HybridTransitionCache {
public:
    static constexpr size_t LinearLimit = 4;

    struct LookupResult {
        Value value{};
        size_t probes{};
        bool hashed{};
    };

    bool Add(const Key& key, Value value) {
        if (promoted) {
            return hashed_values.try_emplace(key, value).second;
        }
        if (std::ranges::find(keys, key) != keys.end()) {
            return false;
        }
        keys.push_back(key);
        values.push_back(value);
        if (keys.size() > LinearLimit) {
            hashed_values.reserve(keys.size());
            for (size_t index = 0; index < keys.size(); ++index) {
                hashed_values.try_emplace(keys[index], values[index]);
            }
            keys.clear();
            values.clear();
            promoted = true;
        }
        return true;
    }

    [[nodiscard]] LookupResult Lookup(const Key& key) const noexcept {
        if (promoted) {
            const auto it = hashed_values.find(key);
            return {it != hashed_values.end() ? it->second : Value{}, 1, true};
        }
        for (size_t index = 0; index < keys.size(); ++index) {
            if (keys[index] == key) {
                return {values[index], index + 1, false};
            }
        }
        return {Value{}, keys.size(), false};
    }

    [[nodiscard]] size_t Size() const noexcept {
        return promoted ? hashed_values.size() : keys.size();
    }

    [[nodiscard]] bool IsPromoted() const noexcept {
        return promoted;
    }

private:
    std::vector<Key> keys;
    std::vector<Value> values;
    ankerl::unordered_dense::map<Key, Value, Hash> hashed_values;
    bool promoted{};
};

} // namespace Vulkan
