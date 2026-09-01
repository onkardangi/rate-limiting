# ADR-005: Atomic Redis Counter with INCR

## Context

Phase 4 moved rate-limit state into Redis so multiple `rate-limiter-service` instances could observe the same counters. However, the implementation still used a GET → inspect → SET read-modify-write flow.

That allowed lost updates across instances:

```
Limiter A reads 99
Limiter B reads 99
Limiter A writes 100
Limiter B writes 100
```

Shared state alone does not make state transitions atomic.

## Options Considered

- **Keep GET → inspect → SET** — Simple, but still allows lost updates.
- **Application-level locking** — Would serialize updates and add coordination complexity.
- **Redis INCR** — Redis performs each increment atomically on the server.
- **Lua script / transaction** — Would make increment and TTL atomic together, but solves a problem we have not yet isolated.

## Decision

Use Redis `INCR` as the counter mutation primitive. `RateLimitStateStore` exposes an increment-oriented operation, and `RateLimiterService` deals only with rate-limit policy.

When a key is first created (`INCR` returns 1), configure expiration with a separate `EXPIRE` command.

## Trade-offs

**Benefits:**

- No lost updates between rate-limiter instances
- Exactly 100 requests can consume a 100-request quota under concurrent access
- Storage atomicity is hidden behind the state-store abstraction

**Costs:**

- `INCR` and `EXPIRE` are still separate Redis commands
- A crash between them can leave a key without TTL
- Phase 5A intentionally does not solve this

## What This Does Not Solve

The next unresolved question is:

**How do we make counter creation/increment and TTL behavior atomic as one Redis operation?**

This ADR also does not solve:

- Failure handling when Redis is unavailable
- Fixed-window boundary bursts
- Trusted user identity
- Configurable rate-limit policies
- Hot-key behavior at very large scale
