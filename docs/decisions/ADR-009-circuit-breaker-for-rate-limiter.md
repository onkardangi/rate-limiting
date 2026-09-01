# ADR-009: Circuit Breaker for Rate Limiter Dependency

## Context

Phase 7 bounded how long app-service waits for an individual rate-limiter call. During a sustained outage, every request still spent timeout and network resources attempting to reach the rate-limiter service. Repeated calls could also add load to an already degraded dependency.

Fail-open policy helps only after unavailability is detected. Without a circuit breaker, the application keeps calling a dependency that is already known to be unhealthy.

## Options Considered

- **Keep calling with timeouts only** — Simple, but wastes latency and resources during sustained failure.
- **Retry failed calls** — Might recover transient errors, but increases latency and can amplify load on a degraded service.
- **Circuit breaker** — Stop calling after enough dependency failures; periodically probe for recovery.
- **Hand-rolled breaker state machine** — Flexible, but reinvents a well-understood pattern.

## Decision

Add a Resilience4j circuit breaker around app-service → rate-limiter-service HTTP calls.

- Dependency failures (connection errors, timeouts, HTTP 5xx) count toward breaker health.
- Business outcomes `ALLOWED` and `RATE_LIMITED` do not count as failures.
- When the breaker is `OPEN`, no remote call is made; `RateLimitClient` returns `UNAVAILABLE`.
- Existing app-service fail-open policy in `RateLimitFilter` continues unchanged.

## Circuit-Breaker States

**CLOSED** — Calls flow normally. Failures are counted.

**OPEN** — Calls are short-circuited. No HTTP request is made to rate-limiter-service.

**HALF_OPEN** — A small number of probe calls are permitted. Success moves toward `CLOSED`; failure reopens the breaker.

## Why no retry is added

- Rate limiting is on the critical request path.
- Retry increases latency.
- Retries can amplify traffic to a degraded service.
- Retry policy should be considered separately.

## Trade-offs

**Benefits:**

- Avoids repeated calls to a known-unhealthy dependency
- Reduces wasted latency and resources during outages
- Gives the dependency time to recover

**Costs:**

- Temporary loss of live dependency checks while `OPEN`
- Configuration can be too aggressive or too lenient
- Circuit breaker adds operational complexity

Configuration values (failure-rate threshold, window size, open wait duration) are SLO/capacity decisions, not universal constants.

## What This Does Not Solve

Even with timeouts and a circuit breaker, many concurrent requests can consume threads and connections while the rate-limiter dependency is slow but has not yet tripped the circuit.

**Next question:** How much concurrent work should we allow toward the rate-limiter dependency?

This ADR also does not solve:

- Bulkheads or concurrency limits
- Metrics and alerting backends
- Local fallback rate limiting
- Redis replication or clustering
- Trusted user identity
