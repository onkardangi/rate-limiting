# ADR-001: In-Memory Fixed-Window Rate Limiter

## Context

We need to enforce a limit of 100 requests per fixed calendar minute per user. The application currently runs as a single instance with no shared infrastructure.

## Options Considered

- **In-memory counter** — Store per-user window and count inside the application process.
- **External/shared datastore** — Store counters in a networked store shared across processes.

## Decision

Use an in-memory fixed-window counter.

There is only one application instance, so introducing networked or shared infrastructure would solve a problem we do not currently have.

## Trade-offs

**Benefits:**

- Simple to implement and reason about
- Very low latency (local memory access)
- No external dependency or network hop

**Costs:**

- State is lost on process restart
- State cannot be shared across future application instances
- Fixed-window boundary bursts (up to 200 requests across a minute boundary)
- The current implementation does not guarantee correct counting under concurrent requests

## What This Does Not Solve

- Concurrency safety under simultaneous requests
- Multiple application instances
- Distributed coordination
- Strict rolling-window semantics
- Dynamic or configurable rate-limit policies
- Trusted user identity
- Persistence of rate-limit state
- Failure handling at distributed scale
