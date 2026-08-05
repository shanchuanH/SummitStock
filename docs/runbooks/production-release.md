# Production release runbook

## Preconditions

- Replace every placeholder credential and set `SESSION_COOKIE_SECURE=true` behind TLS.
- Confirm the database backup completed and was restored into a disposable MySQL instance.
- Record the image digest, strategy version, migration head, and OpenAPI checksum.
- Confirm the broker/provider integration is read-only and no order-writing credential is present.

## Release

1. Build one immutable backend image and deploy that digest for both API and Worker.
2. Run `docker compose -f infra/compose.yaml --profile ops run --rm migration` once. Stop if Flyway validation or JPA validation fails.
3. Start API, wait for liveness/readiness, then start one Worker and observe lease/job metrics.
4. Run `scripts/smoke.ps1`. Verify data health is explicit; `EMPTY` or `STALE` is acceptable, fabricated readiness is not.
5. Confirm there are no dead EOD jobs, no unexpected recommendations, and no execution endpoint.

## Rollback

Stop the Worker first, then API. Redeploy the previous compatible image. Flyway migrations are forward-only; do not edit or undo an applied migration. If rollback requires schema/data restoration, create a new database from the verified backup and switch the application connection only after validation.

## Incident checks

Inspect request IDs, hashed user identifiers, job IDs, error codes, pending/dead counts, EOD age, provider failures, and DB pool metrics. Never copy session IDs, passwords, provider keys, full broker payloads, or tax identifiers into tickets.
