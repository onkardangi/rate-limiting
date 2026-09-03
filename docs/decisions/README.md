# Architecture Decision Records

Architectural decisions discovered during this project will be recorded here.

Each decision documents the problem encountered, the options considered, the choice made, and the trade-offs accepted. This keeps the reasoning behind the system's evolution visible as the project grows.

## Format

```markdown
# ADR-XXX: Title

## Context

What problem did we encounter?

## Options Considered

What possible solutions did we consider?

## Decision

What did we choose and why?

## Trade-offs

What did we gain and what did we sacrifice?

## What This Does Not Solve

What limitations remain?
```

| ADR | Title |
|-----|-------|
| [ADR-001](ADR-001-in-memory-fixed-window.md) | In-Memory Fixed-Window Rate Limiter |
| [ADR-002](ADR-002-concurrency-safe-per-user-updates.md) | Concurrency-Safe Per-User Updates |
| [ADR-003](ADR-003-dedicated-rate-limiter-service.md) | Dedicated Rate Limiter Service |
| [ADR-004](ADR-004-redis-shared-rate-limit-state.md) | Redis Shared Rate-Limit State |
| [ADR-005](ADR-005-atomic-redis-counter-with-incr.md) | Atomic Redis Counter with INCR |
| [ADR-006](ADR-006-atomic-counter-and-expiration-with-lua.md) | Atomic Counter and Expiration with Lua |
| [ADR-007](ADR-007-fail-open-rate-limiter-unavailability.md) | Fail Open on Rate Limiter Unavailability |
| [ADR-008](ADR-008-bounded-rate-limiter-wait-time.md) | Bounded Rate Limiter Wait Time |
| [ADR-009](ADR-009-circuit-breaker-for-rate-limiter.md) | Circuit Breaker for Rate Limiter Dependency |
| [ADR-010](ADR-010-bulkhead-rate-limiter-concurrency.md) | Bulkhead for Rate Limiter Concurrency |
| [ADR-011](ADR-011-rate-limit-policy-abstraction.md) | Rate-Limit Policy Abstraction |
| [ADR-012](ADR-012-exact-sliding-window-log.md) | Exact Sliding Window Log Rate Limiting |
