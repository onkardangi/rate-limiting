# Scalable Rate Limiter

This project is an incremental system-design exercise that starts with a simple single-node application and evolves toward a production-oriented distributed rate-limiting system.

The purpose is to explore architectural decisions rather than jumping directly to a final architecture.

## Goals

- Learn system design through iterative architecture.
- Understand why distributed-system components become necessary.
- Measure and reason about scalability.
- Document architectural decisions and trade-offs.
- Build a portfolio-quality backend project.

## Approach

Each phase follows the same pattern:

```
Simple solution
  → Observe limitation
  → Understand the cause
  → Evaluate alternatives
  → Make an architectural decision
  → Implement it
  → Test/measure it
  → Find the next limitation
```

Problems drive the design. Infrastructure and abstractions are introduced only when a concrete limitation justifies them.

## Current State

**Phase 1 — Fixed Window**

A single Spring Boot application with a product endpoint protected by an in-memory fixed-window rate limiter.

| Endpoint | Description |
|----------|-------------|
| `GET /api/products/{id}` | Returns a hardcoded product response (rate limited) |

All requests to `/api/products/*` require the `X-User-Id` header.

## Phase 1 — Fixed Window

We currently run one application instance. Rate-limit state therefore lives in application memory.

Users are temporarily identified using the `X-User-Id` request header. This is a learning simplification — it is **not** a secure production identity mechanism and will eventually be replaced by a trusted identity source.

Each user receives **100 requests per fixed calendar minute**. Request 101 within the same minute returns HTTP 429. The implementation uses a fixed-window counter: one counter per user, reset when the calendar minute changes.

### Why this design

- Simplest design satisfying the current requirement
- Very fast local access
- No network dependency
- Minimal infrastructure
- Easy to understand and test

This is not the final architecture. It is the starting point from which future limitations will motivate change.

## Known Limitations

### Fixed-window boundary bursts

A user can send 100 requests at the end of one minute and another 100 immediately at the beginning of the next minute. This is valid under our current fixed-window semantics but would not satisfy a strict rolling-60-second requirement.

### Single-instance state

Counters exist only inside this application process. Multiple instances would each maintain independent counters.

### Process restart

Restarting the application loses all rate-limit state.

### Concurrency

The current simple implementation is not designed to guarantee correct counting under concurrent requests. Two threads can both read the same count and both increment it, allowing more than 100 requests in a window.

### Caller identity

`X-User-Id` is client-controlled and therefore not suitable as a trusted identity mechanism in production.

These limitations are intentional. Future phases will use them to motivate architectural changes.

## Architecture

```
Client
  |
  v
Spring Boot Application
  |
  +--> Rate Limit Filter (X-User-Id, allow/deny)
  |
  v
Product Endpoint
```

Rate-limit state: in-memory `HashMap` (user → window + count).

## Planned Evolution

Future phases may explore:

- Rate-limiting algorithms
- Concurrency
- Distributed state
- Atomic operations
- Failure handling
- Horizontal scaling
- Observability
- Load testing

Specific technologies and solutions will be chosen as problems emerge during development.

## Architecture Decision Records

Design decisions are documented in [`docs/decisions/`](docs/decisions/).

## Requirements

- Java 21
- Maven 3.9+

## Build and Run

```bash
mvn clean package
java -jar target/scalable-rate-limiter-0.1.0-SNAPSHOT.jar
```

## Test

```bash
mvn test
```

## Example

```bash
curl -H "X-User-Id: alice" http://localhost:8080/api/products/123
```

```json
{
  "id": "123",
  "name": "Example Product"
}
```

When the rate limit is exceeded:

```json
{
  "error": "Rate limit exceeded"
}
```
