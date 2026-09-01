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

**Phase 7 — Bounded Rate Limiter Wait Time**

A Maven multi-module project with bounded HTTP timeouts between app-service and rate-limiter-service:

| Module | Port | Responsibility |
|--------|------|----------------|
| `app-service` | 8080 | Product API; calls rate limiter with configured timeouts; fails open on timeout |
| `rate-limiter-service` | 8081 | Fixed-window rate limiting; atomic counters in Redis |
| Redis | 6379 | Shared ephemeral counters for all rate-limiter instances |

| Endpoint | Service | Description |
|----------|---------|-------------|
| `GET /api/products/{id}` | app-service | Returns a hardcoded product response (rate limited; fails open on limiter timeout) |
| `POST /api/rate-limit/check` | rate-limiter-service | Internal rate-limit check |

All requests to `/api/products/*` require the `X-User-Id` header.

Rate-limit decisions: `ALLOWED`, `RATE_LIMITED`, `UNAVAILABLE`.

## Phase 1 — Fixed Window

We started with one application instance. Rate-limit state lived in application memory.

Each user receives **100 requests per fixed calendar minute**. Request 101 within the same minute returns HTTP 429.

## Phase 2 — Concurrency Safety

Phase 1's plain `HashMap` allowed read-modify-write races under concurrent requests. The fix was `ConcurrentHashMap.compute(...)` to make the entire state transition atomic per user key within one JVM.

## Phase 3 — Dedicated Rate Limiter Service

Rate limiting was extracted into a dedicated service so multiple application instances could share one logical limiter. State remained in-memory inside the rate-limiter service.

## Phase 4 — Redis Shared State

Rate-limit state moved to Redis so multiple rate-limiter instances could observe the same counters. Updates intentionally used GET → inspect → SET, which still allowed lost updates across instances.

## Phase 5A — Atomic Redis Counter with INCR

Redis `INCR` fixed distributed lost-update races. However, `INCR` and `EXPIRE` were still separate commands — a crash between them could leave a key without TTL.

## Phase 5B — Atomic Counter and Expiration with Lua

### The problem

Phase 5A could leave permanent keys in Redis:

```
INCR creates key
service crashes
EXPIRE never runs
key has no TTL
```

### The solution

Execute `INCR` and conditional `EXPIRE` inside one Redis Lua script. Redis runs the script atomically on the server. The `RateLimitStateStore.increment(...)` contract is unchanged.

```lua
count = INCR key
if count == 1 then
  EXPIRE key ttl
end
return count
```

### Why Lua

- Correctness spans multiple Redis commands
- Moving the operation server-side removes the client-side crash gap
- No distributed lock is required
- `RateLimiterService` stays unaware of Redis details

### What is now solved

Distributed counter correctness for this fixed-window design: increment and initial TTL assignment are atomic as one operation.

## Phase 6 — Fail Open on Rate Limiter Unavailability

When the rate limiter cannot produce a decision (for example, Redis failure), it returns `UNAVAILABLE`. For `GET /api/products/{id}`, app-service fails open and serves the product. A warning is logged so operators can distinguish outage-driven allows from normal allows.

## Phase 7 — Bounded Rate Limiter Wait Time

### The problem

Fail-open only helps after unavailability is detected. A slow rate limiter can block the application indefinitely if there is no timeout.

### The solution

Configure explicit HTTP client timeouts on the app-service → rate-limiter-service call:

```properties
rate-limiter.client.connection-timeout=500ms
rate-limiter.client.read-timeout=1s
```

- **Connection timeout** — how long to wait to establish a connection
- **Read timeout** — how long to wait for a response

When a timeout occurs, `RateLimitClient` returns `UNAVAILABLE` and the existing fail-open policy continues the product request.

Production timeout values should be derived from latency SLOs and observed percentile latency, not treated as universal constants.

### Important limitation

Even with timeouts, every request still attempts to call an unhealthy rate-limiter service during sustained outages.

**Next question:** Should we keep calling a dependency we already know is unhealthy?

## Known Limitations

### Repeated calls to unhealthy dependencies

Timeouts bound wait time, but app-service still calls the rate-limiter on every request. No circuit breaker has been added.

### Redis availability

The rate-limiter service depends on Redis. No retries or circuit breakers have been added.

### Fixed-window boundary bursts

A user can send 100 requests at the end of one minute and another 100 immediately at the beginning of the next minute.

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
Redis (Lua: INCR + conditional EXPIRE on rate-limit:{userId}:{windowMinute})
```

## Planned Evolution

Future phases may explore:

- Circuit breaking for unhealthy dependencies
- Observability and timeout metrics
- Load testing

Specific technologies and solutions will be chosen as problems emerge during development.

## Architecture Decision Records

Design decisions are documented in [`docs/decisions/`](docs/decisions/).

## Requirements

- Java 21
- Maven 3.9+
- Docker (for local Redis via Docker Compose)

## Build and Run

Build all modules:

```bash
mvn clean package
```

Start Redis:

```bash
docker compose up -d
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

Tests use an in-memory state store and do not require a running Redis instance:

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
