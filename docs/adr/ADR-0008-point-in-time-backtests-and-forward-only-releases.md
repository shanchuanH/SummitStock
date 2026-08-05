# ADR-0008: Point-in-time backtests and forward-only releases

- Status: Accepted
- Date: 2026-08-05

## Decision

Backtesting is a separate pure-Java module. It consumes completed bars, applies signals only at the next available open, models configurable slippage and opening gaps, adjusts holdings for splits and cash dividends, respects ETF listing/delisting dates, preserves Core/Active sleeve identity, and blocks incomplete data, survivorship bias, and OOS leakage. Expanding walk-forward folds never overlap training and test periods.

Backtest reports are user-scoped, immutable completed runs keyed by strategy version, period, universe checksum, configuration checksum, and idempotency key. Metrics include regime accuracy, stop protection, ETF outcomes, 5/21/63-day recommendation returns, urgency precision, HOLD frequency, quantity compliance, and active-sleeve accountability. The report API is read-only and the UI never fabricates a missing result.

Production releases use one image for migration, API, and Worker. Migration mode is non-web and exits after Flyway/JPA validation. Applied migrations are forward-only. Backup/restore is verified against a disposable database before release. CI blocks high-severity dependency findings, leaked secrets, API drift, failing Playwright flows, and failed image builds.

## Consequences

- Backtest results cannot be interpreted as same-close executable fills.
- A universe that omits delisted instruments is rejected instead of silently overstating performance.
- Acknowledgements and reports remain decision support; no order endpoint or broker write credential exists.
- Rollback uses a previous compatible image or a separately restored database, never an edited Flyway migration.

## Migration and validation

Flyway `V8__backtest_runs_and_metrics.sql` adds idempotent run and metric tables. Golden tests cover next-open/gap/slippage, corporate actions, lifecycle and bias gates, sleeve/quantity behavior, walk-forward folds, required metrics, MySQL scope/idempotency, HttpOnly/SameSite sessions, dependency audit, browser flows, one-shot migration, runtime smoke, and backup/restore.
