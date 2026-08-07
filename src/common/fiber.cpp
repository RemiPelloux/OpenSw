// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

// SPDX-FileCopyrightText: Copyright 2020 yuzu Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

#include <array>
#include <cstdlib>
#include <thread>
#include <mutex>

#ifdef _WIN32
#include <windows.h>
#elif !defined(__OPENORBIS__)
#include <sys/mman.h>
#include <unistd.h>
#endif

#include "common/assert.h"
#include "common/fiber.h"
#include "common/virtual_buffer.h"

#include <boost/context/detail/fcontext.hpp>

namespace Common {

#ifdef __OPENORBIS__
constexpr size_t DEFAULT_STACK_SIZE = 128 * 4096;
#else
constexpr size_t DEFAULT_STACK_SIZE = 512 * 4096;
#endif
constexpr u32 CANARY_VALUE = 0xDEADBEEF;

class GuardedStack {
public:
    explicit GuardedStack(size_t size_) : size{size_} {
#ifdef _WIN32
        SYSTEM_INFO info{};
        GetSystemInfo(&info);
        guard_size = info.dwPageSize;
        allocation_size = size + 2 * guard_size;
        allocation = VirtualAlloc(nullptr, allocation_size, MEM_RESERVE, PAGE_NOACCESS);
        if (allocation) {
            data_ptr = static_cast<u8*>(VirtualAlloc(static_cast<u8*>(allocation) + guard_size,
                                                     size, MEM_COMMIT, PAGE_READWRITE));
        }
#elif defined(__OPENORBIS__)
        data_ptr = static_cast<u8*>(std::malloc(size));
        allocation = data_ptr;
        allocation_size = size;
#else
        guard_size = static_cast<size_t>(sysconf(_SC_PAGESIZE));
        allocation_size = size + 2 * guard_size;
        allocation = mmap(nullptr, allocation_size, PROT_NONE,
                          MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
        if (allocation != MAP_FAILED) {
            data_ptr = static_cast<u8*>(allocation) + guard_size;
            if (mprotect(data_ptr, size, PROT_READ | PROT_WRITE) != 0) {
                munmap(allocation, allocation_size);
                allocation = MAP_FAILED;
                data_ptr = nullptr;
            }
        }
#endif
        if (!data_ptr) {
            throw std::bad_alloc{};
        }
    }

    ~GuardedStack() {
#ifdef _WIN32
        if (allocation) {
            VirtualFree(allocation, 0, MEM_RELEASE);
        }
#elif defined(__OPENORBIS__)
        std::free(allocation);
#else
        if (allocation != MAP_FAILED) {
            munmap(allocation, allocation_size);
        }
#endif
    }

    GuardedStack(const GuardedStack&) = delete;
    GuardedStack& operator=(const GuardedStack&) = delete;

    u8* data() const {
        return data_ptr;
    }

private:
    size_t size{};
    size_t guard_size{};
    size_t allocation_size{};
    void* allocation{};
    u8* data_ptr{};
};

struct Fiber::FiberImpl {
    explicit FiberImpl(bool needs_stack = false) {
        if (needs_stack) {
            stack = std::make_unique<GuardedStack>(DEFAULT_STACK_SIZE);
        }
    }

    u32 canary_1 = CANARY_VALUE;
    std::unique_ptr<GuardedStack> stack;
    u32 canary_2 = CANARY_VALUE;

    boost::context::detail::fcontext_t context{};
    boost::context::detail::fcontext_t rewind_context{};

    std::mutex guard;
    std::function<void()> entry_point;
    std::function<void()> rewind_point;
    std::shared_ptr<Fiber> previous_fiber;

    u8* stack_limit = nullptr;
    bool is_thread_fiber = false;
    bool released = false;
};

void Fiber::SetRewindPoint(std::function<void()>&& rewind_func) {
    impl->rewind_point = std::move(rewind_func);
}

Fiber::Fiber(std::function<void()>&& entry_point_func) : impl{std::make_unique<FiberImpl>(true)} {
    impl->entry_point = std::move(entry_point_func);
    impl->stack_limit = impl->stack->data();
    u8* stack_base = impl->stack_limit + DEFAULT_STACK_SIZE;
    impl->context = boost::context::detail::make_fcontext(stack_base, DEFAULT_STACK_SIZE, [](boost::context::detail::transfer_t transfer) -> void {
        auto* fiber = static_cast<Fiber*>(transfer.data);
        ASSERT(fiber && fiber->impl && fiber->impl->previous_fiber && fiber->impl->previous_fiber->impl);
        ASSERT(fiber->impl->canary_1 == CANARY_VALUE);
        ASSERT(fiber->impl->canary_2 == CANARY_VALUE);
        fiber->impl->previous_fiber->impl->context = transfer.fctx;
        fiber->impl->previous_fiber->impl->guard.unlock();
        fiber->impl->previous_fiber.reset();
        fiber->impl->entry_point();
        UNREACHABLE();
    });
}

Fiber::Fiber() : impl{std::make_unique<FiberImpl>()} {}

Fiber::~Fiber() {
    if (!impl->released) {
        // Make sure the Fiber is not being used
        const bool locked = impl->guard.try_lock();
        ASSERT_MSG(locked, "Destroying a fiber that's still running");
        if (locked) {
            impl->guard.unlock();
        }
    }
}

void Fiber::Exit() {
    ASSERT_MSG(impl->is_thread_fiber, "Exiting non main thread fiber");
    if (impl->is_thread_fiber) {
        impl->guard.unlock();
        impl->released = true;
    }
}

void Fiber::YieldTo(std::weak_ptr<Fiber> weak_from, Fiber& to) {
    to.impl->guard.lock();
    to.impl->previous_fiber = weak_from.lock();

    auto transfer = boost::context::detail::jump_fcontext(to.impl->context, &to);
    // "from" might no longer be valid if the thread was killed
    if (auto from = weak_from.lock()) {
        if (from->impl->previous_fiber == nullptr) {
            ASSERT(false && "previous_fiber is nullptr!");
        } else {
            from->impl->previous_fiber->impl->context = transfer.fctx;
            from->impl->previous_fiber->impl->guard.unlock();
            from->impl->previous_fiber.reset();
        }
    }
}

std::shared_ptr<Fiber> Fiber::ThreadToFiber() {
    std::shared_ptr<Fiber> fiber = std::shared_ptr<Fiber>{new Fiber()};
    fiber->impl->guard.lock();
    fiber->impl->is_thread_fiber = true;
    return fiber;
}

} // namespace Common
