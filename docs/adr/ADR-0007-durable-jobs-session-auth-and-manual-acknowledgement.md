# ADR-0007: Durable jobs, session auth, and manual acknowledgement

- Status: Accepted
- Date: 2026-08-05

## Decision

Scheduled methods only scan time and write idempotent work into MySQL. Workers claim one due job with `FOR UPDATE SKIP LOCKED`, commit a five-minute lease, and record each attempt before executing. Expired leases return to the pending queue; transient failures use bounded exponential retry and exhausted work becomes `DEAD`. Quote slots, the 14-step EOD pipeline, weekly memos, and monthly reviews have stable idempotency keys. Page requests never enqueue jobs.

Private APIs use Spring Security sessions persisted in MySQL, same-origin CSRF protection, HttpOnly cookies, audited login outcomes, and per-identity login throttling. The SPA stores no authentication token. Recommendation acknowledgement is durable and idempotent, but always reports `executionSubmitted=false`.

## Consequences

- API and worker processes remain stateless and use the same application artifact.
- A worker crash cannot silently lose claimed work; an operator can inspect pending and dead counts.
- Repeated scanners, retries, acknowledgements, and page refreshes do not duplicate side effects.
- The UI has calm empty/error/loading states and keeps brokerage execution outside the system.

## Migration and validation

Flyway `V7__durable_worker_auth_audit_and_ack.sql` adds jobs, attempts, security events, and recommendation acknowledgements. Integration tests cover idempotency, lease recovery, retries, page-load isolation, login rate limiting, and acknowledgement without execution. Component and Playwright tests cover the ten-page workspace and session-cookie policy.
