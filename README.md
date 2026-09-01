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

**Phase 3 — Dedicated Rate Limiter Service**

A Maven multi-module project with two services:

| Module | Port | Responsibility |
|--------|------|----------------|
| `app-service` | 8080 | Product API; delegates rate-limit checks over HTTP |
| `rate-limiter-service` | 8081 | Fixed-window rate limiting; owns in-memory state |

| Endpoint | Service | Description |
|----------|---------|-------------|
| `GET /api/products/{id}` | app-service | Returns a hardcoded product response (rate limited) |
| `POST /api/rate-limit/check` | rate-limiter-service | Internal rate-limit check |

All requests to `/api/products/*` require the `X-User-Id` header.

## Phase 1 — Fixed Window

We started with one application instance. Rate-limit state lived in application memory.

Each user receives **100 requests per fixed calendar minute**. Request 101 within the same minute returns HTTP 429. The implementation uses a fixed-window counter: one counter per user, reset when the calendar minute changes.

## Phase 2 — Concurrency Safety

Phase 1's plain `HashMap` allowed read-modify-write races under concurrent requests. The fix was `ConcurrentHashMap.compute(...)` to make the entire state transition atomic per user key.

## Phase 3 — Dedicated Rate Limiter Service

### The problem

Phase 2 is correct within one JVM, but if multiple application servers each own rate-limit state, counters fragment. A user could exceed the limit by spreading requests across instances.

### The solution

Extract rate limiting into a dedicated service. Application instances call it over HTTP before serving protected requests. Rate-limit state remains in-memory inside the rate-limiter service for now.

```
Client
  |
  v
Application Service (app-service)
  |
  +--> POST /api/rate-limit/check (HTTP)
  |
  v
Rate Limiter Service (rate-limiter-service)
  |
  v
in-memory ConcurrentHashMap
```

### Why this design

- Centralizes rate-limit logic and state
- Multiple app instances can share one logical limiter
- Policy can evolve independently of application code
- No shared datastore yet — we have not demonstrated the need to scale the rate-limiter service itself

### Trade-offs accepted

- Extra network hop per protected request
- Rate-limiter service is an availability dependency
- Single rate-limiter instance is a bottleneck and single point of failure
- In-memory state still prevents horizontally scaling the rate-limiter service

## Known Limitations

### Shared state across rate-limiter instances

The rate-limiter service still holds state in memory. Multiple rate-limiter instances would each maintain independent counters. The next problem to solve: **how can multiple rate-limiter service instances share the same rate-limit state?**

### Fixed-window boundary bursts

A user can send 100 requests at the end of one minute and another 100 immediately at the beginning of the next minute.

### Process restart

Restarting the rate-limiter service loses all rate-limit state.

### Network dependency

The application service depends on the rate-limiter service being available. No retries or circuit breakers have been added.

### Caller identity

`X-User-Id` is client-controlled and therefore not suitable as a trusted identity mechanism in production.

These limitations are intentional. Future phases will use them to motivate architectural changes.

## Architecture

```
Client
  |
  v
app-service (:8080)
  |
  +--> Rate Limit Filter (X-User-Id validation, HTTP check)
  |
  +--> Product Endpoint
  |
  v (HTTP)
rate-limiter-service (:8081)
  |
  v
ConcurrentHashMap (per-user window + count)
```

## Planned Evolution

Future phases may explore:

- Shared state across rate-limiter instances
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

Build all modules:

```bash
mvn clean package
```

Start the rate-limiter service (port 8081):

```bash
java -jar rate-limiter-service/target/rate-limiter-service-0.1.0-SNAPSHOT.jar
```

Start the application service (port 8080):

```bash
java -jar app-service/target/app-service-0.1.0-SNAPSHOT.jar
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
