# ADR-011: Rate-Limit Policy Abstraction

## Context

The rate-limiter-service currently enforces a single fixed-window algorithm with hardcoded limits. Different services, endpoints, and products can require different rate-limiting semantics:

- **Fixed window** — simple and cheap, but allows boundary bursts.
- **Sliding window log** — exact rolling-window semantics (e.g. never more than 100 requests in any rolling 60-second period) at higher memory cost.
- **Token bucket** — sustained-rate control with explicit burst allowance; does not guarantee an exact rolling-window limit.

Treating one algorithm as universally best would force incorrect trade-offs. Redis is an infrastructure detail for distributed state — it should not leak into the public policy abstraction.

## Options Considered

1. **Keep algorithm logic inside `RateLimiterService`** — Simple today, but every new algorithm requires editing the service and mixing policy selection with enforcement.
2. **Single giant configuration object** — One type holding every algorithm's fields; awkward validation and unclear ownership.
3. **Policy abstraction with algorithm-specific configuration** — Separate policy types, small context object, resolver for selection, Redis beneath the chosen policy.

## Decision

Introduce a `RateLimitPolicy` abstraction:

```
RateLimitPolicyResolver  →  "What rule applies?"
RateLimitPolicy          →  "How is the rule enforced?"
RateLimitStateStore      →  "How is distributed state maintained?"
```

- `RateLimitContext` carries request identity (and can grow with resource identifiers later).
- `FixedWindowRateLimitPolicy` preserves the existing working behavior.
- `SlidingWindowLogRateLimitPolicy` and `TokenBucketRateLimitPolicy` exist as intentional extension points with algorithm-specific configuration types, but are not implemented yet.
- `DefaultRateLimitPolicyResolver` resolves all requests to the fixed-window policy for now.

Upper-layer code uses:

```java
decision = policy.check(context);
```

not Redis-specific operations.

## Algorithm Notes

### Fixed window (current default)

- Configuration: `limit`, `windowDuration`
- Low state cost; calendar-aligned windows allow boundary bursts.

### Sliding window log (future)

For each request at time `T`:

1. Remove timestamps `<= T - windowDuration`
2. Count remaining timestamps
3. If count `>= limit` → `RATE_LIMITED`
4. Otherwise record `T` → `ALLOWED`

The remove → count → decide → add operation must be atomic in Redis (likely Lua). A non-atomic version is not production-safe.

### Token bucket (future)

State: available tokens and last refill time. Atomic transition will compute refill from elapsed time, cap at capacity, consume if allowed, persist, and return the decision. Refill rate controls sustained throughput; capacity controls burst allowance.

## Trade-offs

**Benefits:**

- Algorithms can be selected from requirements rather than assumed universal
- Redis remains an implementation detail beneath policies
- Existing fixed-window behavior is preserved behind a stable seam

**Costs:**

- Additional types and indirection for a single active algorithm today
- Future algorithms still require careful atomic Redis design

## What This Does Not Solve

- Sliding window log and token bucket Redis/Lua implementations (later phases)
- Dynamic per-endpoint rule configuration or a policy DSL
- Fixed-window boundary bursts (the motivating question for a future algorithm phase)

**Next question:** Does fixed-window rate limiting actually provide the traffic behavior we want?
