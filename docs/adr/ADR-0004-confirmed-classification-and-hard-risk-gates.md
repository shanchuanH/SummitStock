# ADR-0004: Confirmed classification and hard portfolio risk gates

- Status: Accepted
- Date: 2026-08-05
- Owners: Portfolio Engine

## Context

Packet 04 introduces accounts, positions and lots, holding classification, clusters, risk snapshots, holding analyses, recommendations, and user decisions. Advice must not infer stock quality from a ticker, mix unvested compensation with liquid assets, exceed explicit position/risk budgets, or produce exact quantities from degraded evidence.

## Decision

Keep classification and risk-policy evaluation in pure-Java `strategy-core`. Every suggested classification requires explicit user confirmation; the confirmation updates a user-scoped position under optimistic concurrency and writes an audit event. Hard caps are 15% for Quality, 10% for Thematic, 5% for Tactical, and 2% for Speculative holdings. Per-trade, total-stock, and cluster risk budgets are enforced together with the 48-hour cooling period, invalid-averaging, cost-basis anchoring, and unvested-compensation blocks. Non-healthy evidence may return ranges and reasons but cannot return a precise quantity.

Persist source portfolio records and append-only risk/analysis/recommendation evidence in MySQL with decimal columns, strategy versions, data-as-of timestamps, validity, checksums, and Rule IDs. API responses serialize financial decimals as strings. All portfolio reads and writes are scoped by the authenticated user's email.

## Alternatives

- Infer Quality Stock from a recognized company name or price performance: rejected because quality needs fundamental evidence and user confirmation.
- Store one position per ticker: rejected because Core and Tactical overlays may coexist for the same ticker.
- Enforce limits only in the UI: rejected because API clients and future workers must share the same deterministic policy.
- Issue exact quantities from stale evidence: rejected as false precision.

## Consequences

### Positive

- Classification overrides are explicit, versioned, user-scoped, and auditable.
- Risk rules are deterministic and reusable by API and future worker calculations.
- `Today Actions` cannot overwhelm the user with more than three `MUST_ACT` items.
- The UI displays server-owned decimals and policy results without portfolio formulas.

### Negative

- Classification requires an extra confirmation step.
- Production recommendations remain empty until evidence-producing workers write versioned snapshots.
- Exact-quantity advice is intentionally unavailable during stale or suspect data conditions.

## Migration / Rollback

Flyway `V4__portfolio_risk_and_holding_intelligence.sql` adds account, equity, position/lot, tax-lot, cluster, risk, holding-analysis, recommendation, and decision tables. Applied migrations are immutable; rollback disables Packet 04 writers and routes.

## Validation

- Decimal preservation and MySQL 8.4 V4 migration tests
- Hard cap, total/cluster risk, cooling, invalid averaging, anchoring, and stale-evidence tests
- User-isolation, optimistic-concurrency, audit, DXYZ/UNKNOWN, and maximum-three action API tests
- UI tests for portfolio data, classification restraint, stale quantity blocking, and authentication-required state

## Related Rule IDs / Strategy Versions

- `POSITION.CLASSIFY.001`, `POSITION.UNVESTED.001`
- `RISK.WEIGHT.001`, `RISK.TRADE.001`, `RISK.TOTAL.001`, `RISK.CLUSTER.001`
- `RISK.COOLING.001`, `RISK.AVERAGING.001`, `RISK.ANCHORING.001`
- `DATA.STALE.002`
- Strategy draft `1.0.0-draft`
