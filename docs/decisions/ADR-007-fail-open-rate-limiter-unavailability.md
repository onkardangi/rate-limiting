# ADR-007: Fail Open on Rate Limiter Unavailability

## Context

Rate limiting now depends on a dedicated service and Redis. Those dependencies can fail independently of the application service.

Infrastructure failure is different from a legitimate rate-limit rejection:

- **ALLOWED** — the user is within their quota
- **RATE_LIMITED** — the user exceeded their quota
- **UNAVAILABLE** — the system could not obtain a rate-limit decision

For `GET /api/products/{id}`, product availability is prioritized over strict rate-limit enforcement during outages.

## Options Considered

- **Fail closed** — reject requests when the rate limiter is unavailable. Protects abuse limits but makes product reads unavailable during infrastructure outages.
- **Fail open** — allow requests when the rate limiter is unavailable. Preserves product availability but weakens abuse protection during outages.
- **Hide failure as ALLOWED** — treat infrastructure failure the same as a normal allow decision. Simpler, but indistinguishable in logs and policy.
- **Explicit UNAVAILABLE decision with endpoint-specific policy** — model all three outcomes; let each endpoint choose how to handle UNAVAILABLE.

## Decision

Model `ALLOWED`, `RATE_LIMITED`, and `UNAVAILABLE` explicitly.

- The rate-limiter service reports `UNAVAILABLE` when it cannot make a rate-limit decision (for example, when Redis fails).
- The app-service owns failure policy per endpoint.
- `GET /api/products/{id}` fails open: `UNAVAILABLE` continues to the product controller.
- A warning is logged when failing open so operators can distinguish normal allows from outage-driven allows.

## Why the policy does not live in RedisRateLimitStateStore

- The datastore layer should report infrastructure failure, not product policy.
- Fail-open vs fail-closed is an application concern that may differ by endpoint.
- A security-sensitive endpoint such as login might reasonably choose fail closed or another fallback strategy.

## Trade-offs

**Fail open:**

- Preserves product availability during rate-limiter or Redis outages
- Users may temporarily exceed limits while protection is weakened
- Abuse protection is reduced until the limiter recovers

**Explicit UNAVAILABLE:**

- Clear separation between quota enforcement and infrastructure failure
- Enables observability without pretending an outage was a normal allow
- Requires a typed contract between services

## What This Does Not Solve

Redis or the rate-limiter service may not fail immediately. They may instead become very slow.

Example:

- normal rate-limit request: 2 ms
- degraded rate-limit request: 500 ms / several seconds

Fail-open behavior does not help if the application waits too long before deciding the limiter is unavailable.

**Next question:** How long should the application wait for the rate limiter before treating it as unavailable?
