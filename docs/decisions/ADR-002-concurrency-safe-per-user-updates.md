# ADR-002: Concurrency-Safe Per-User Updates

## Context

The Phase 1 in-memory implementation is incorrect under concurrent requests due to read-modify-write races. Two threads can both read the same count, both decide a request is allowed, and both update state — allowing more than 100 requests in a window.

The invariant spans both the current window and the current count. A transition from `(oldWindow, oldCount)` to `(newWindow, newCount)` must be treated consistently.

## Options Considered

1. **Global synchronized access** — Serialize all rate-limit checks behind one lock.
2. **Per-user explicit locking** — Maintain a lock registry keyed by user ID.
3. **ConcurrentHashMap with per-key atomic compute/update** — Use `ConcurrentHashMap.compute(...)` so each user's state transition is atomic.
4. **AtomicInteger-based counters** — Atomically increment a per-user counter.

## Decision

Use `ConcurrentHashMap` with a per-key atomic state transition via `compute(...)`.

## Why

- Ensures correctness for same-user concurrent requests
- Avoids global serialization — unrelated users do not share rate-limit state
- Simpler than maintaining a separate lock registry
- Naturally matches the rate-limit key as the coordination boundary

A global synchronized method would be correct but would serialize all callers, including users with no shared state. `AtomicInteger` alone is insufficient because atomically incrementing only the count does not make window reset and count update atomic together.

## Trade-offs

**Benefits:**

- Correct concurrent counting for the same user
- Unrelated users can proceed independently
- Still simple and in-memory
- No external infrastructure

**Costs:**

- Hot users can still contend on their own key
- Still single-process only
- Still loses state on restart
- Does not coordinate across multiple application instances

## What This Does Not Solve

- Multiple application instances
- Distributed state
- Persistence
- Process restart state loss
- Fixed-window boundary bursts
- Trusted identity
- Configurable rate-limit policies
- Hot-key behavior at very large scale
- Distributed failure handling
