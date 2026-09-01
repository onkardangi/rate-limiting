# ADR-010: Bulkhead for Rate Limiter Concurrency

## Context

A rate-limiter dependency may become slow without immediately failing. At high request throughput, many calls can remain in flight simultaneously, consuming application threads, HTTP connections, memory, and other resources.

- **Timeouts** bound how long an individual call can wait.
- **Circuit breakers** answer whether we should attempt a dependency currently considered unhealthy.

Neither directly bounds how many calls may be in flight while the dependency is still considered healthy.

## Options Considered

1. **Unbounded concurrency** — Simple, but risks resource exhaustion when the limiter is slow.
2. **Queue excess requests** — Smooths bursts, but adds latency and can create a backlog on the critical path.
3. **Immediately reject excess limiter calls** — Protects application resources; causes some requests to bypass rate limiting because the current product endpoint fails open.

## Decision

Add a Resilience4j **semaphore bulkhead** around rate-limiter HTTP calls in `RateLimitClient`.

- Bounded concurrent limiter calls via `max-concurrent-calls`.
- Zero wait for permits (`max-wait-duration=0`) — no request queue.
- Saturation maps to `RateLimitDecision.UNAVAILABLE`.
- Existing fail-open policy in `RateLimitFilter` continues unchanged for `GET /api/products/{id}`.

### Resilience responsibilities

| Mechanism | Question |
|-----------|----------|
| **Circuit breaker** | Should we try the dependency? |
| **Bulkhead** | Do we currently have capacity to try? |
| **Timeout** | How long are we willing to wait for the attempt? |

### Composition order

```
Request → Circuit Breaker (outer) → Bulkhead (inner) → HTTP call
```

When the circuit is `OPEN`, the request is rejected as `UNAVAILABLE` without acquiring bulkhead capacity. When the circuit is `CLOSED`, the bulkhead decides whether local concurrency capacity exists.

### Why bulkhead rejection does not count as remote dependency failure

A bulkhead rejection means app-service has reached the concurrency budget allocated to this dependency. No remote HTTP call occurred, so it is not evidence that rate-limiter-service failed. `BulkheadFullException` is therefore ignored by the circuit breaker.

### Why semaphore bulkhead (not thread-pool bulkhead)

The dependency call is synchronous HTTP on the request thread. The goal is a simple concurrency cap, not a separate worker queue or pool.

### Why max-wait remains zero

Queueing consumes latency budget on the critical path. For this endpoint, fail-open is preferable to building a backlog of waiting threads.

## Trade-offs

**Benefits:**

- Protects threads, connections, and other app-service resources
- Prevents dependency slowness from creating unbounded concurrency
- Fast degradation to existing fail-open behavior

**Costs:**

- Requests may bypass rate limiting under temporary saturation
- Capacity must be tuned per environment

**Too-small concurrency limit:** unnecessary `UNAVAILABLE` results and excessive fail-open traffic.

**Too-large concurrency limit:** weak resource isolation; app-service may still suffer resource exhaustion.

Production capacity should be derived from expected throughput, limiter latency, application thread capacity, HTTP connection capacity, acceptable fail-open rate, and measured production behavior — not treated as a universal default.

## What This Does Not Solve

Our infrastructure is now more resilient, but the fixed-window algorithm still permits boundary bursts. For example:

- 100 requests at 12:00:59
- 100 requests at 12:01:00

Both windows individually satisfy the 100/minute rule, but roughly 200 requests can arrive within about one second.

**Next question:** Does fixed-window rate limiting actually provide the traffic behavior we want?

This ADR also does not solve:

- Retries
- Metrics and alerting backends
- Local fallback rate limiting
- Redis replication or clustering
- Trusted user identity
- Sliding window or token-bucket algorithms
