# PROJECT_STATE

## Current Phase

Rescue Phase 1 completed on 2026-08-05. Phase 0 was completed from audited baseline `ff6396a421940598dd32b81a58b17e9fc90ce4f8`; Phases 2–7 remain pending and must be implemented in order.

Phase 0 removed the incorrect first-position/free-form classification flow and the hard-coded trade preview, made the Dashboard distinguish session, authentication, loading, empty-portfolio, and API-failure states, and prohibited unconfirmed “NO URGENT ACTION” conclusions. Fake providers are now restricted to the explicit `local-fixture` and `test` profiles; the default provider mode is disabled and unsafe non-fixture startup fails closed.

Phase 1 introduced the backend-owned `PortfolioAnalysisState`, ordered readiness calculation, and `GET /api/v1/brief/today`. The contract aggregates persisted portfolio, cash, market, holding-analysis, recommendation, data-quality, job, and event evidence with Decimal String money values. The Dashboard now makes exactly one brief request and renders explicit loading, authentication/error, no-portfolio, partial, stale, blocked, failed, ready-with-actions, and ready-without-actions states. “NO URGENT ACTION” is possible only for `ANALYSIS_READY` with all three action queues empty.

## Completed Packets

- `01_FOUNDATION_AND_ARCHITECTURE.md`
- `02_DATABASE_MARKET_DATA_AND_QUANT.md`
- `03_REGIME_DRAWDOWN_AND_DATA_QUALITY.md`
- `04_PORTFOLIO_RISK_AND_HOLDING_INTELLIGENCE.md`
- `05_STOPS_THESIS_VALUATION_AND_EARNINGS.md`
- `06_ETF_DIP_RECOMMENDATIONS_AND_CASHFLOW.md`
- `07_WORKER_FRONTEND_AUTH_AND_JOURNAL.md`
- `08_BACKTEST_SECURITY_DEPLOYMENT_AND_ACCEPTANCE.md`

## Stack

- JDK 25.0.4, Maven Wrapper 3.9.11
- Spring Boot 4.1.0, Spring Modulith 2.1.0
- MySQL 8.4 LTS / InnoDB / utf8mb4 / UTC / strict SQL mode
- React 19.2.7 / React Router 8.3 / Vite 8.1 / strict TypeScript; Node 22.22+
- Maven reactor + pnpm workspace
- Manual execution only
- Replaceable fake EOD and SEC/IR provider adapters

## Strategy

- Draft: `1.0.0-draft`
- Published: none
- Benchmarks: SPY, QQQ
- Emergency cash: $20,000; excluded from deployable capital by `CASH.EMERGENCY.001`
- Monthly income/expense/surplus: $11k / $4k / $7k
- 15%: market-driven ETF dip mode
- 20%: pain line
- Amazon RSU: unvested, non-liquid, non-tradable by `COMPENSATION.UNVESTED.001`

## Architecture

- Pure-Java quant and strategy libraries
- Pure-Java backtest library with completed-bar/next-open fills, corporate actions, ETF lifecycle, bias gates, and expanding walk-forward OOS folds
- Quant results use explicit `READY`, `WARMING_UP`, `MISSING_DATA`, and `INVALID_DATA` states
- Regime uses fixed Trend/Momentum/Breadth/Stress weights with documented hard overrides
- Drawdown uses high-water marks and 8/10/12/15/20 control states with market/position/mixed source classification
- Holding classifications require explicit server-side position selection and confirmation; the incomplete browser classifier is disabled during the rescue implementation
- Hard position, per-trade, total-stock, cluster, cooling-period, averaging, anchoring, and evidence-quality gates run in pure Java
- Individual-stock stops are EOD, structure/ATR based, monotonic, and separated into soft/close-confirmed/catastrophic levels; Core ETFs are exempt
- Thesis state is structured, source-backed, expiring, and user-confirmed; Quality Discount and earnings policies are classification aware
- ETF Dip requires complete, market-driven 15% drawdown evidence, setup score 60+, two triggers, emergency protection, unique 20/25/30/25 tranches, and five-day cooldown
- Monthly cashflow fills the $20k emergency floor first; no-signal Quality allocation falls back to Broad Core
- Spring modular monolith with verified module boundaries
- Same backend artifact for API and non-web Worker modes
- React SPA contains no portfolio formulas
- MySQL is the durable source of truth
- Scheduled scanners write idempotent quote/EOD/weekly/monthly jobs; workers use `SKIP LOCKED`, leases, bounded retry, and dead-letter status
- Authentication uses JDBC-backed HttpOnly sessions, CSRF, login throttling, and audited outcomes; the browser stores no access token
- Recommendation acknowledgement is idempotent and never submits an order
- No Redis, Celery, queues, or microservices

## Database

- Flyway head: `V8__backtest_runs_and_metrics.sql`
- Foundation tables: app user, strategy version, investment policy, cash bucket, audit log, Spring Session
- Market tables: instrument, price bar, quote, corporate action, provider request, data quality event, fundamental observation, company event, indicator snapshot
- Raw and adjusted bars have separate identities; indicator snapshots are append-only and provenance-keyed
- Market regime and portfolio drawdown snapshots are append-only and keyed by strategy version, data-as-of, and evidence checksum
- Account, equity, position/lot, tax-lot, cluster, risk, holding-analysis, recommendation, and action-decision tables are user scoped and versioned
- Thesis/source, stop/alert, valuation, earnings-risk, and trade-journal evidence is versioned; stop alerts are deduplicated per position/type/date
- ETF dip event/tranche, cashflow allocation, and active-sleeve accountability snapshots are evidence-keyed; each event/tranche is unique
- Job run/attempt, authentication security event, and recommendation acknowledgement records are durable and idempotency constrained
- Backtest runs and metrics are user scoped, checksum/idempotency keyed, and store explicit bias/OOS boundaries
- Hibernate: schema validation only
- Pending migrations: none

## API Contract

- OpenAPI: initialized at `contracts/openapi/portfolio-api.json`
- Generated client: initialized at `contracts/generated/src/schema.d.ts`
- Runtime endpoints: unified Today Brief, portfolio/position/market context, ETF Dip/cashflow/accountability, recommendation acknowledgement, auth session/CSRF, worker health, and read-only backtest reports
- Errors: RFC 9457 Problem Details enabled; request IDs returned as `X-Request-ID`

## Providers

- Explicit `local-fixture`/`test` EOD adapter: deterministic fake implementation
- Explicit `local-fixture`/`test` filing/facts adapter: deterministic fake SEC/IR implementation
- Default mode: `disabled`; non-fixture runtimes fail closed when configured with disabled or fake providers
- Provider calls use bounded retry, rate limiting, durable attempt status, source timestamps, checksums, freshness, normalization versions, and quality status
- Fidelity remains a future read-only/manual boundary

## Tests

- Pure-Java invariant tests
- Golden tests for SMA, EMA, Wilder ATR, RSI, MACD, rolling high, realized volatility, relative strength, confirmed/candidate swings, and earnings gaps
- Golden tests for Healthy/Narrow/Panic/Missing regimes, hard overrides, stale gates, individual-stock loss, benchmark-confirmed 15% drawdown, and Pain Line
- jqwik property tests for moving-average invariants and indicator bounds
- Explicit quant-core dependency boundary test proving Spring is absent
- Spring Modulith architecture verification
- MySQL 8.4 Testcontainers + Flyway/JPA validation
- UTC and strict SQL-mode checks
- Spring Security, CSRF, and JDBC-session smoke test
- Stable validation Problem Details envelope test
- OpenAPI export/contract test
- MySQL batch upsert, raw/adjusted separation, provider journaling, snapshot append/idempotency, and V2 migration integration test
- Provider retry, backoff, rate-limit, malformed-payload, and freshness tests
- React Testing Library/Vitest health, honest-unavailable, market inspection, and symbol-normalization states
- React context-page tests for ready, stale, empty, and authentication-required states
- Pure-Java holding classification and hard-risk policy tests
- V4 user-isolation, optimistic-concurrency, classification audit, decimal-string, stale-quantity, and Today Actions cap integration tests
- React portfolio-page tests for summary/positions, classification restraint, stale precision blocking, and authentication-required state
- Executive Brief contract/readiness tests backed by persisted MySQL evidence, including no-portfolio, queued, missing-market, partial, ready with/without actions, failed, and false-no-action prevention
- Dashboard tests proving the page requests only Today Brief, caps MUST_ACT at three, and distinguishes partial/error states from a confirmed ready empty queue
- Stop, R/MFE/MAE, Quality Discount, earnings class policy, thesis concurrency, duplicate alert, and Position Detail tests
- ETF market attribution, setup/trigger/cooldown/tranche, emergency-first cashflow, fallback, accountability, and Dip UI tests
- Durable job idempotency/claim/lease/retry/scanner tests, login throttling/audit tests, acknowledgement-without-execution tests, and ten-page workspace tests
- Backtest next-open/slippage/gap, split/dividend, ETF lifecycle, Core/Active, quantity, bias, walk-forward/OOS, decision-metric, V8 scope/idempotency, and report UI tests
- CI dependency/secret/image gates, Playwright navigation, migration-mode exit, runtime smoke, and V8 backup/restore verification

## Known Issues

- Local development credentials are placeholders and must be replaced outside localhost.
- Local runtime starts with seeded SPY/QQQ instruments but no fetched observations, so data health correctly reports `EMPTY` until ingestion runs.
- Local runtime has no synthesized regime/drawdown rows; the context API correctly reports `EMPTY` until evidence calculation runs.
- Production EOD and SEC/IR credentials/adapters are not implemented; non-fixture startup therefore remains intentionally blocked until a production provider is selected.
- Import batches and first-class analysis runs are introduced in Phase 2; until then, the brief truthfully reports `PORTFOLIO_READY` when positions have no analysis snapshots and leaves `analysisRunId` empty.
- Local runtime starts without private account/position data or synthesized recommendations; authenticated portfolio APIs correctly return empty collections until data is imported.
- Production recommendation generation remains dormant until real, quality-gated provider and portfolio data are configured; an order lifecycle is intentionally absent.
- Strategy `1.0.0-draft` remains unpublished and local credentials remain placeholders.

## Next Step

Implement Rescue Phase 2: Fidelity CSV upload, parse/preview/confirm, versioned import batches, persisted analysis runs, and idempotent analysis job orchestration.
