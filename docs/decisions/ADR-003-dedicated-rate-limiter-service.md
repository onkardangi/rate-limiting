# ADR-003: Dedicated Rate Limiter Service

## Context

Phase 2 works correctly within one JVM. The rate limiter is concurrency-safe and keeps state in a `ConcurrentHashMap` with per-key atomic updates.

However, if multiple application servers each own rate-limit logic and state, counters become fragmented. Each instance would enforce its own independent limit, allowing a user to exceed the intended quota by spreading requests across instances.

We want one central place for rate-limit policy and implementation so that all application instances share a single logical limiter.

## Options Considered

- **Keep rate limiting inside each application instance** — Simple, but counters fragment across instances.
- **Extract rate limiting into a dedicated service** — Centralize logic behind a network boundary; application instances delegate checks over HTTP.
- **Introduce shared external state immediately (e.g. Redis)** — Would solve multi-instance coordination, but adds infrastructure before we have demonstrated the need to scale the rate-limiter service itself.

## Decision

Extract rate limiting into a dedicated `rate-limiter-service`. The `app-service` calls it over HTTP before serving product requests. State remains in-memory for now.

## Trade-offs

**Benefits:**

- Centralized rate-limit logic and state
- Multiple application instances can share one logical limiter
- Rate-limit policy can evolve independently of application code

**Costs:**

- Extra network hop on every protected request
- The rate-limiter service becomes an availability dependency for the application
- A single rate-limiter instance becomes a bottleneck and single point of failure
- In-memory state still prevents horizontally scaling the rate-limiter service itself

## What This Does Not Solve

The next unresolved problem is:

**How can multiple rate-limiter service instances share the same rate-limit state?**

This ADR also does not solve:

- Persistence across process restarts
- Fixed-window boundary bursts
- Trusted user identity
- Configurable rate-limit policies
- Failure handling when the rate-limiter service is unavailable
- Hot-key contention at very large scale
