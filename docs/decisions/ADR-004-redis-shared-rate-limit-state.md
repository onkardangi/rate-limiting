# ADR-004: Redis Shared Rate-Limit State

## Context

Phase 3 centralized rate limiting in one dedicated JVM. That service became a bottleneck and single point of failure. Scaling it horizontally would fragment in-memory counters — each instance would enforce its own independent limit, allowing users to exceed the intended quota.

We need multiple `rate-limiter-service` instances to observe the same rate-limit state.

## Options Considered

- **Keep in-memory state** — Simple, but prevents horizontal scaling of the rate-limiter service.
- **Relational database** — Durable and queryable, but heavier than needed for ephemeral counters.
- **Redis** — Low-latency key/value access with TTL support, well suited to ephemeral counters.

## Decision

Move ephemeral rate-limit state to Redis. Multiple `rate-limiter-service` instances use the same Redis instance for shared visibility.

For this phase, state updates intentionally use a simple GET → inspect → SET flow. We have not yet made the update atomic across instances.

## Why Redis

- Low-latency key/value access
- Ephemeral state with TTL support
- Appropriate for counters
- No relational model required for fixed-window counts

## Trade-offs

**Benefits:**

- State is shared across JVMs
- Rate-limiter instances can theoretically scale horizontally
- Process restart no longer necessarily loses rate-limit state

**Costs:**

- Additional infrastructure dependency
- Network round-trip on every state read/write
- GET → inspect → SET is a multi-command read-modify-write operation
- Multiple limiter instances can observe stale values and overwrite each other
- Shared state does not imply atomic state transitions

## What This Does Not Solve

The next unresolved question is:

**How do we make the Redis rate-limit update atomic across multiple rate-limiter-service instances?**

This ADR also does not solve:

- Distributed atomicity under concurrent requests
- Failure handling when Redis is unavailable
- Fixed-window boundary bursts
- Trusted user identity
- Configurable rate-limit policies
- Hot-key behavior at very large scale
