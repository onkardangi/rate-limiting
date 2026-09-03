# ADR-012: Exact Sliding Window Log Rate Limiting

## Context

Fixed-window rate limiting allows boundary bursts: a user can consume a full quota at the end of one window and another full quota at the start of the next. The product question from ADR-011 remains: does fixed-window provide the traffic behavior we want?

For endpoints that require a strict rule such as "never more than 100 accepted requests in any rolling 60-second period," fixed window is insufficient.

Token bucket is also not equivalent: it controls sustained rate and burst allowance but does not guarantee an exact rolling-window maximum.

## Options Considered

1. **Keep fixed window** — Simple and cheap, but boundary bursts remain.
2. **Bucketed sliding-window counter** — Lower memory, but approximate rather than exact.
3. **Exact sliding window log** — Store accepted-request timestamps; exact rolling-window semantics at higher memory cost.
4. **Token bucket** — Good for sustained throughput + bursts, not exact rolling-window limits.

## Decision

Implement **exact sliding window log** as `SlidingWindowLogRateLimitPolicy` using Redis Sorted Sets and one atomic Lua script.

### State model

- One Redis Sorted Set per identity: `rate-limit:sliding:{identity}`
- Score = accepted request timestamp (milliseconds from Redis `TIME`)
- Member = unique request identifier (UUID string from Java)
- Rejected requests are **not** stored

### Boundary semantics

At time `T`, accepted requests in the interval **(T − windowDuration, T]** count toward the limit. Entries with score `<= T − windowDuration` are removed before counting.

### Atomic Lua algorithm

```
now = Redis TIME (milliseconds)
window_start = now - window_ms
ZREMRANGEBYSCORE key -inf window_start
count = ZCARD key
if count >= limit: return RATE_LIMITED (0)
ZADD key now member
EXPIRE key ttl_seconds
return ALLOWED (1)
```

### Why Redis TIME

Multiple rate-limiter-service instances may have clock skew. Using Redis `TIME` inside the script provides one consistent clock source for state transitions.

### Why unique members

Multiple requests may occur in the same millisecond. Scores are not unique; members must be. Timestamp alone cannot be the member.

### Why TTL

`EXPIRE` is garbage collection only, set to `2 × windowDuration` on accepted requests. TTL does not define rolling-window correctness; `ZREMRANGEBYSCORE` does. TTL must remain longer than any accepted timestamp that could still affect a decision.

### State-store abstraction

`SlidingWindowLogStateStore` is separate from `RateLimitStateStore` (fixed-window increment). This keeps each store interface focused rather than growing one mega-interface.

### Production default unchanged

`DefaultRateLimitPolicyResolver` still resolves to `FixedWindowRateLimitPolicy`. Sliding window log is available for tests and future targeted configuration.

## Trade-offs

**Benefits:**

- Exact rolling-window semantics
- No boundary burst at window alignment points
- Atomic remove/count/decide/add via Lua

**Costs:**

- Memory: one ZSET entry per accepted request in the active window
- CPU: ZSET maintenance per request
- Hot-key risk at very high per-identity throughput (future scaling concern)

## What This Does Not Solve

- Token bucket implementation
- Dynamic per-endpoint policy selection / DSL
- Redis Cluster hash-tagging for hot keys
- Multi-quota user+tenant enforcement
- Metrics, replication, multi-region

**Next scaling question:** How do hot keys and Redis Cluster affect per-identity sliding-window logs at high throughput?
