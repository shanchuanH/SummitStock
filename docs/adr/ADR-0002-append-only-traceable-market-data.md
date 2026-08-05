# ADR-0002: Traceable market data and append-only indicator snapshots

- Status: Accepted
- Date: 2026-08-05
- Owners: Portfolio Engine

## Context

Packet 02 requires reproducible indicators and honest handling of incomplete, stale, malformed, raw, and adjusted market data. Provider adapters must remain replaceable without coupling financial mathematics to Spring or network concerns.

## Decision

Store normalized provider observations in MySQL with provider, source timestamp, checksum, normalization version, quality status, and `data_as_of`. Keep raw and adjusted bars as distinct identities. Write indicator results as append-only snapshots identified by instrument, market date, parameters, adjustment mode, and source checksum. Keep all indicator algorithms in pure-Java `quant-core`; Spring orchestrates provider calls, retries, request journaling, batch persistence, and read APIs.

## Alternatives

- Recompute indicators on every API request: rejected because results would not have stable provenance.
- Overwrite one current indicator row: rejected because it destroys auditability when source data changes.
- Hide provider metadata behind a generic payload: rejected because freshness and quality must be queryable.
- Put provider SDKs in `quant-core`: rejected because it violates the deterministic pure-domain boundary.

## Consequences

### Positive

- Every exposed value has an explicit source and freshness boundary.
- Repeated ingestion is idempotent while revised source checksums create new snapshots.
- Provider failures and attempts are durable and inspectable.
- Indicator warm-up and missing-data states are represented instead of fabricated.

### Negative

- Append-only snapshots consume more storage than overwriting current values.
- Consumers must choose adjusted versus raw bars explicitly.
- Provider normalization changes require a new version and may produce new snapshots.

## Migration / Rollback

Flyway `V2__market_data_and_quant.sql` is forward-only. Rollback disables ingestion and read routes; an applied migration is never edited or dropped in place.

## Validation

- Golden and jqwik property tests for quantitative indicators
- MySQL 8.4 Testcontainers migration, batch, JSON, provenance, and idempotency tests
- Provider retry, rate-limit, and non-retryable error tests
- OpenAPI export, generated client typecheck, UI tests, and production build

## Related Rule IDs / Strategy Versions

- No new strategy Rule ID; Packet 02 supplies evidence, not recommendations.
- Strategy draft `1.0.0-draft`
