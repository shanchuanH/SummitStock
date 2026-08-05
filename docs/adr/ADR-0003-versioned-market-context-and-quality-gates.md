# ADR-0003: Versioned market context and data-quality gates

- Status: Accepted
- Date: 2026-08-05
- Owners: Portfolio Engine

## Context

Portfolio drawdown alone cannot determine whether an ETF dip response is appropriate. Packet 03 requires broad-market evidence, explicit high-water marks, source attribution, hard regime overrides, and honest degradation when evidence is stale, partial, suspect, or missing.

## Decision

Compute regime and drawdown classifications in pure-Java `strategy-core`. Regime uses fixed 40/20/20/20 Trend/Momentum/Breadth/Stress weights and documented hard overrides. Drawdown uses the 8/10/12/15/20 control states; 15% is market-driven only when benchmark drawdown and breadth/stress independently confirm it. Persist both results as append-only snapshots keyed by strategy version, `dataAsOf`, and evidence checksum. Missing data returns `WAIT_FOR_DATA`; stale data caps regime and blocks precise critical-drawdown source advice.

## Alternatives

- Treat every 15% portfolio drawdown as market-driven: rejected because concentrated stock losses would incorrectly enable a broad ETF response.
- Store only a current mutable regime: rejected because historical decisions would not be replayable.
- Let the UI derive scores or percentages: rejected because deterministic investment logic belongs in Java.
- Silently reuse stale evidence: rejected because it creates false confidence and false precision.

## Consequences

### Positive

- Market-driven and position-specific losses have explicit, auditable evidence.
- High-water marks never move down.
- Hard overrides and quality gates carry stable Rule IDs.
- The UI presents server-computed values and honest empty/authentication states.

### Negative

- A stale critical snapshot may return `WAIT_FOR_DATA` during a volatile period.
- Snapshot history consumes storage and needs future retention policy decisions.
- Position and cluster attribution depend on Packet 04 data for production calculation.

## Migration / Rollback

Flyway `V3__regime_drawdown_and_quality.sql` adds append-only regime and drawdown tables. Rollback disables writers/read routes; applied migrations are not edited or destructively reverted.

## Validation

- Golden tests: Healthy, Narrow, Panic, Missing, hard override, individual-stock loss, two 15% benchmark scenarios, Pain Line, stale critical evidence
- MySQL 8.4 empty migration, JSON, decimal-string API, and duplicate-evidence tests
- UI tests for ready, stale, empty, and authentication-required states

## Related Rule IDs / Strategy Versions

- `REGIME.OVERRIDE.001`, `REGIME.OVERRIDE.002`
- `REGIME.TACTICAL.001`, `REGIME.BREADTH.001`
- `DATA.STALE.001`, `DATA.MISSING.001`
- `DRAWDOWN.FREEZE.001`, `DRAWDOWN.TACTICAL.001`, `DRAWDOWN.WATCH.001`
- `DRAWDOWN.MARKET.001`, `DRAWDOWN.POSITION.001`, `DRAWDOWN.PAIN.001`
- Strategy draft `1.0.0-draft`
