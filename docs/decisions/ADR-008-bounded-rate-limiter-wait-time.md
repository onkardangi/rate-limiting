# ADR-008: Bounded Rate Limiter Wait Time

## Context

Phase 6 introduced fail-open behavior for `GET /api/products/{id}` when the rate limiter cannot produce a decision. Fail-open only helps after unavailability is detected.

A dependency may be slow rather than immediately failing. Without bounded wait time, rate-limiter latency becomes application latency. Users would wait indefinitely for a product response while the app-service blocks on a hung rate-limiter call.

## Options Considered

- **No timeout** — Simple, but allows unbounded latency propagation.
- **Explicit HTTP client timeouts** — Bound connection and response wait time; treat timeout as inability to obtain a rate-limit decision.
- **Retry on timeout** — Might recover transient slowness, but increases request-path latency and can amplify load against a degraded limiter.

## Decision

Configure explicit HTTP client timeouts between app-service and rate-limiter-service.

- **Connection timeout** — how long to wait to establish a connection
- **Read/response timeout** — how long to wait for the remote service to respond

When a timeout occurs, `RateLimitClient` returns `UNAVAILABLE`. The existing app-service fail-open policy continues the product request.

Timeout values are configuration properties, not hard-coded constants. Production values should be derived from latency SLOs and observed percentile latency rather than treated as universal values.

## Why no retry is added

- Rate limiting is on the critical request path
- Retry increases latency
- Retries can amplify load against an already degraded limiter
- Retry policy should be considered separately

## Trade-offs

**Short timeout:**

- Protects application latency
- May fail open during temporary latency spikes

**Long timeout:**

- Gives the limiter more time to respond
- Increases user-visible latency and resource occupancy

Timeout values are an SLO/capacity decision, not a universal constant.

## What This Does Not Solve

Even with timeouts, every request still attempts to call an unhealthy rate-limiter service. During a sustained outage, this can cause repeated failed calls and waste resources.

**Next question:** Should we keep calling a dependency we already know is unhealthy?

This ADR also does not solve:

- Circuit breaking or bulkheads
- Metrics and alerting for timeout rates
- Fail-closed policies for other endpoints
- Fixed-window boundary bursts
- Trusted user identity
