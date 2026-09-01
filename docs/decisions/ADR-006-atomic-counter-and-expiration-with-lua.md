# ADR-006: Atomic Counter and Expiration with Lua

## Context

Phase 5A used atomic Redis `INCR` to fix lost updates across rate-limiter instances. However, `INCR` and `EXPIRE` were still separate commands executed from the application.

If a process crashed after `INCR` but before `EXPIRE`, a rate-limit key could remain in Redis without expiration — a permanent counter leak.

## Options Considered

- **Keep separate INCR and EXPIRE** — Simple, but the client-side crash gap remains.
- **Redis transaction (MULTI/EXEC)** — Could group commands, but still initiated from the client with similar failure modes before commit.
- **Redis Lua script** — Executes `INCR` and conditional `EXPIRE` atomically on the Redis server.
- **Distributed lock around INCR + EXPIRE** — Adds coordination complexity without moving work server-side.

## Decision

Execute `INCR` and conditional expiration inside a Redis Lua script. Redis runs the script atomically. The `RateLimitStateStore` contract remains unchanged.

## Why Lua is justified here

- Correctness spans multiple Redis commands
- Moving the operation server-side removes the client-side crash gap between `INCR` and `EXPIRE`
- No distributed lock is necessary
- `RateLimiterService` remains unaware of Redis implementation details

## Trade-offs

**Benefits:**

- Counter increment and initial TTL assignment are atomic as one server-side operation
- No observable successful state where a newly created key lacks expiration
- Distributed counter correctness is now complete for this fixed-window design

**Costs:**

- Lua introduces datastore-specific logic
- Scripts require careful testing and observability
- Long-running scripts would block Redis, so the script must stay tiny

## What This Does Not Solve

Distributed counter correctness is no longer the primary unresolved issue. Remaining production concerns include:

- Redis availability
- Timeout behavior
- Fail-open vs fail-closed policy when Redis is unavailable
- Rate-limiter service availability
- Fixed-window boundary bursts
- Trusted user identity
- Configurable rate-limit policies
