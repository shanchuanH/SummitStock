# PROJECT_STATE

## Current Phase

Transformation Phases T00-T04 completed on 2026-08-09 from exact audited baseline `f015a3ed665a9bef38fc03beaea255ee2f21b7ea` on branch `transformation/analyst-intelligence-v2`. T05 is next; phases will continue in the V2 playbook order without pre-T08 visual restructuring.

Transformation baseline: the first backend run failed only because Docker Desktop was not running (`ENVIRONMENT`, 40 Testcontainers initialization errors). After Docker was started, the unchanged baseline passed 108 backend tests (8 quant, 23 strategy, 6 backtest, 71 backend), 27 frontend tests, frontend lint, and production build. The existing frontend bundle-size warning remains non-blocking.

T00 introduced immutable Strategy V2 (`2.0.0-draft`) with configurable resource location, publish state, SHA-256 config hash, fail-closed emergency exclusion, and an explicit `underweightAloneCanTriggerAdd=false` guardrail. V14 adds append-only, owner-scoped capital snapshots. `CapitalBaseService` now derives invested tradable assets, tracked/emergency/deployable cash, investable assets, total liquid assets, and unvested compensation from durable records. Position/cluster weights and risk snapshots use investable assets; total liquid assets remain available for reporting. The current holding engine can no longer emit ADD solely because a position is below target. Targeted capital/weight/guardrail tests and the full 112-test backend suite pass.

T01 separates completed-session decision-price evidence from execution-liquidity evidence. A missing bid/ask now leaves a valid EOD decision price `HEALTHY` while execution liquidity is explicitly `MISSING`; stale completed sessions remain blocked. The US-equity trading calendar drives freshness instead of elapsed wall-clock hours. Market and fundamentals providers expose typed capabilities, provider failures retain the exact rate-limit/plan-limit/timeout/auth/malformed/unavailable taxonomy, and plan limits propagate as non-retryable pipeline failures rather than becoming no-action conclusions. V15 persists quote evidence dimensions and provider capability snapshots. The full 117-test Maven suite passes (8 quant, 23 strategy, 6 backtest, 80 backend).

T02 connects SEC submissions/companyfacts to the durable analysis chain through `CHECK_FILINGS`, `COLLECT_FUNDAMENTALS`, `NORMALIZE_FINANCIALS`, and `COMPUTE_FINANCIAL_HEALTH`. V16 stores canonical periods, provenance-preserving facts, base/derived metric snapshots, and explainable health dimensions. Restatements resolve by latest filing, concept mappings and health thresholds are configuration backed, new filings expire derived valuation/recommendation evidence, and missing facts can never default to healthy. Canonical health now feeds holding and brief readiness while legacy evidence remains readable during migration. The full 123-test Maven suite passes (8 quant, 23 strategy, 6 backtest, 86 backend).

T03 adds a vendor-neutral `EstimateDataProvider`, canonical EPS/revenue estimate history, and 7/30/90-day revision intelligence. V17 persists observations and explainable revision snapshots with analyst coverage, dispersion, quality, and checksums. The durable chain now runs `COLLECT_ESTIMATES` then `COMPUTE_REVISIONS`; no configured external estimate source yields explicit `MISSING` evidence. Strongly negative revisions block Quality ADD/starter actions, normal ADD requires at least FLAT, and missing/partial estimates lower confidence. The full 127-test Maven suite passes (8 quant, 23 strategy, 6 backtest, 90 backend).

T04 replaces target-gap buying with canonical valuation evidence and quality-gated decisions. V18 stores trailing/forward P/E, EV/Sales, FCF yield, Price/Sales, market cap, separate 3Y/5Y own-history percentiles, growth-adjusted valuation, confidence, and durable starter-event state. `COMPUTE_VALUATION` now follows revisions. High-confidence deep discount requires at least 252 observations, healthy company evidence, and no strongly-negative revision. Quality `STARTER_BUY` and normal `ADD` enforce portfolio, thesis, stop, valuation, revision, trend, and capacity gates; starter sizing is 25% and underweight alone cannot buy. The full 133-test Maven suite passes (8 quant, 23 strategy, 6 backtest, 96 backend).

Phase 0 removed the incorrect first-position/free-form classification flow and the hard-coded trade preview, made the Dashboard distinguish session, authentication, loading, empty-portfolio, and API-failure states, and prohibited unconfirmed “NO URGENT ACTION” conclusions. Fake providers are now restricted to the explicit `local-fixture` and `test` profiles; the default provider mode is disabled and unsafe non-fixture startup fails closed.

Phase 1 introduced the backend-owned `PortfolioAnalysisState`, ordered readiness calculation, and `GET /api/v1/brief/today`. The contract aggregates persisted portfolio, cash, market, holding-analysis, recommendation, data-quality, job, and event evidence with Decimal String money values. The Dashboard now makes exactly one brief request and renders explicit loading, authentication/error, no-portfolio, partial, stale, blocked, failed, ready-with-actions, and ready-without-actions states. “NO URGENT ACTION” is possible only for `ANALYSIS_READY` with all three action queues empty.

Phase 2 added preview-first Fidelity CSV, pasted-table, and manual-holding intake. Confirmed batches reconcile accounts and positions transactionally, preserve fractional quantities and nullable cost basis, append immutable position snapshots, close holdings absent from a later full account snapshot, keep SPAXX as cash, and store unvested RSUs separately from liquid positions. Confirmation is owner-scoped, versioned, audited, idempotent, and creates one durable analysis run/job. The `/portfolio-import` UI requires every error row to be corrected or explicitly ignored before confirmation and never connects to a brokerage account.

Phase 3 added fail-closed production provider configuration, an Alpha Vantage market adapter, and an SEC EDGAR submissions/companyfacts adapter. HTTP calls now use configured timeouts, retry/backoff, concurrency-safe rate limiting, 429/5xx handling, checksums, source/fetch timestamps, warnings, and explicit data-quality states. SEC facts use ten named business mappings while preserving raw concepts and units. Fidelity symbols resolve through durable system/user mappings; unknown symbols create inactive placeholders and positions remain `WAIT_FOR_DATA` until a user mapping resolves them.

Phase 4 replaced the placeholder worker path with a typed `JobHandler` registry and a durable coordinator that records handler output, warnings, timestamps, attempts, retries, permanent failures, and sanitized unexpected errors. Analysis runs now advance through persisted step dependencies rather than scheduled-time offsets. The executable EOD chain collects quotes/bars/actions, validates completed bars, computes indicators and server-owned regime inputs, synchronizes imported snapshots, writes user-scoped drawdown/stops/holding analysis, and generates formal recommendations and a daily digest. Unknown jobs fail permanently, provider failures preserve retryability, weekly/monthly summaries query persisted evidence, and no handler or integration test inserts a final recommendation directly.

Phase 5 replaced placeholder holding outputs with an evidence assembler, classification-specific readiness, YAML-backed immutable strategy definitions, deterministic sizing gates, and hard-risk-first recommendation conflict resolution. Analysis and recommendation snapshots retain strategy/config hashes, winning rules, suppressed candidates, reasons, quantities, validity, and evidence checksums. Classification suggestions are owner-scoped by position ID and never infer company quality from a ticker. GOOGL Quality, DRAM Thematic ETF, and DXYZ Speculative integration fixtures prove distinct templates. Formal daily-close stops are separated from intraday catastrophic quote breaches, and debug previews are unavailable outside explicit test/local-fixture profiles.

Phase 6 replaced technical packet navigation with an analyst-first Chinese workspace for Today Brief, Portfolio, Opportunities, Review, and Settings while retaining diagnostics under advanced/admin routes. The Dashboard presents honest action counts and evidence-rich recommendations, and records handled/deferred/ignored decisions with rationale while explicitly never submitting execution. Portfolio List is owner-scoped, priority-sorted, filterable, and supports only position-row classification confirmation. Position Detail follows the required twelve-module order and renders real completed-bar candlesticks, average cost, formal/soft stops, earnings, and trade markers through Lightweight Charts; missing evidence produces explicit empty states. RTL and Playwright cover the complete login/import/analysis/action/position journey.

Phase 7 added the complete target-portfolio acceptance fixture, including thirteen liquid positions, SPAXX cash, and an unvested AMZN compensation holding that remains excluded from liquid assets and has no tradable quantity. Portfolio Brief now derives liquid assets, core/tactical allocation, technology and employer concentration, cluster/open risk, unvested compensation, and user-scoped drawdown from persisted evidence. Position reports expose classification-aware company, ETF, speculative, and portfolio evidence: GOOGL uses the quality-company model, DRAM uses the thematic-ETF model without company earnings semantics, and DXYZ uses lower-confidence speculative caps and stop/event gates without ticker-based promotion. `RealPortfolioVerticalAcceptanceTest` proves the real import-to-analysis-to-recommendation-to-acknowledgement path through production services, with no direct recommendation insertion and no execution submission.

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

- Draft: `2.0.0-draft` (publish state `DRAFT`, configurable path, immutable hash)
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
- Individual-stock formal stops are completed-daily-close, structure/ATR based, and monotonic; intraday quotes independently evaluate catastrophic alerts; Core ETFs are exempt
- Thesis state is structured, source-backed, expiring, and user-confirmed; Quality Discount and earnings policies are classification aware
- ETF Dip requires complete, market-driven 15% drawdown evidence, setup score 60+, two triggers, emergency protection, unique 20/25/30/25 tranches, and five-day cooldown
- Monthly cashflow fills the $20k emergency floor first; no-signal Quality allocation falls back to Broad Core
- Spring modular monolith with verified module boundaries
- Same backend artifact for API and non-web Worker modes
- React SPA contains no portfolio formulas
- MySQL is the durable source of truth
- Scheduled scanners write idempotent quote/EOD/weekly/monthly jobs; workers use `SKIP LOCKED`, leases, bounded retry, and dead-letter status
- Authentication uses JDBC-backed HttpOnly sessions, CSRF, login throttling, and audited outcomes; the browser stores no access token
- Recommendation acknowledgement records handled/deferred/ignored decisions and rationale idempotently and never submits an order
- No Redis, Celery, queues, or microservices

## Database

- Flyway head: `V18__valuation_engine_v2.sql`
- Foundation tables: app user, strategy version, investment policy, cash bucket, audit log, Spring Session
- Market tables: instrument, price bar, quote, corporate action, provider request, data quality event, fundamental observation, company event, indicator snapshot
- Raw and adjusted bars have separate identities; indicator snapshots are append-only and provenance-keyed
- Market regime and portfolio drawdown snapshots are append-only and keyed by strategy version, data-as-of, and evidence checksum
- Account, equity, position/lot, tax-lot, cluster, risk, holding-analysis, recommendation, and action-decision tables are user scoped and versioned
- Thesis/source, stop/alert, valuation, earnings-risk, and trade-journal evidence is versioned; stop alerts are deduplicated per position/type/date
- ETF dip event/tranche, cashflow allocation, and active-sleeve accountability snapshots are evidence-keyed; each event/tranche is unique
- Job run/attempt, authentication security event, and recommendation acknowledgement records are durable and idempotency constrained
- Job runs and attempts persist structured handler results, warnings, and data-as-of timestamps; analysis runs use durable dependency steps and a unique run key
- Backtest runs and metrics are user scoped, checksum/idempotency keyed, and store explicit bias/OOS boundaries
- Import batches/rows, position snapshots, compensation holdings, and portfolio analysis runs/steps are owner scoped, versioned, and idempotency constrained
- Portfolio capital snapshots are append-only and record Strategy V2 version/hash, investable-vs-liquid capital, emergency exclusion, evidence quality, and checksum
- Quotes persist decision market date/quality independently from execution-liquidity quality and spread; provider capability snapshots are append-only and provider scoped
- Canonical financial periods/facts/metrics/health snapshots preserve SEC taxonomy, concept, unit, accession, form, filing date, source, calculation version, strategy hash, and evidence checksum
- Forward EPS/revenue estimates and 7/30/90-day revision snapshots preserve period/horizon, range, analyst count, dispersion, provider quality, and evidence checksum
- Hibernate: schema validation only
- Instrument aliases, user-scoped manual mappings, and position data readiness are durable and constraint-backed
- Pending migrations: none

## API Contract

- OpenAPI: initialized at `contracts/openapi/portfolio-api.json`
- Generated client: initialized at `contracts/generated/src/schema.d.ts`
- Runtime endpoints: unified Today Brief, owner-scoped Portfolio List, portfolio import preview/confirm/query, position-scoped classification suggestion/confirmation/report/chart/journal, portfolio/position/market context, ETF Dip/cashflow/accountability, recommendation acknowledgement, auth session/CSRF, worker health, and read-only backtest reports
- Errors: RFC 9457 Problem Details enabled; request IDs returned as `X-Request-ID`

## Providers

- Explicit `local-fixture`/`test` EOD adapter: deterministic fake implementation
- Explicit `local-fixture`/`test` filing/facts adapter: deterministic fake SEC/IR implementation
- Production market adapter: Alpha Vantage daily adjusted bars, global quotes, dividends, and splits
- Production fundamentals adapter: SEC EDGAR submissions and explicitly mapped companyfacts with declared User-Agent
- Default market mode: `disabled`; non-fixture runtimes fail closed with disabled/fake providers or missing API key/SEC User-Agent
- Provider calls use bounded retry, rate limiting, durable attempt status, source timestamps, checksums, freshness, normalization versions, and quality status
- Fidelity intake is file/paste/manual only; there is no brokerage connection, credential storage, order placement, or automated account operation

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
- Production provider safety, local mock-server market/SEC contracts, unified data-quality policy, and MySQL instrument-resolution tests
- React Testing Library/Vitest health, honest-unavailable, market inspection, and symbol-normalization states
- React context-page tests for ready, stale, empty, and authentication-required states
- Pure-Java holding classification and hard-risk policy tests
- V4 user-isolation, optimistic-concurrency, classification audit, decimal-string, stale-quantity, and Today Actions cap integration tests
- React portfolio-page tests for summary/positions, classification restraint, stale precision blocking, and authentication-required state
- Executive Brief contract/readiness tests backed by persisted MySQL evidence, including no-portfolio, queued, missing-market, partial, ready with/without actions, failed, and false-no-action prevention
- Dashboard tests proving the page requests only Today Brief, caps MUST_ACT at three, and distinguishes partial/error states from a confirmed ready empty queue
- Fidelity parser and MySQL integration tests for CSV variations, SPAXX, fractional shares, missing cost basis, unvested RSUs, preview immutability, duplicate import, versioned confirmation, owner isolation, durable analysis enqueue, and later-snapshot reconciliation
- Portfolio import UI tests proving Unknown rows block confirmation until corrected or ignored and that confirmation reports an analysis queue rather than execution
- Stop, R/MFE/MAE, Quality Discount, earnings class policy, thesis concurrency, duplicate alert, and Position Detail tests
- ETF market attribution, setup/trigger/cooldown/tranche, emergency-first cashflow, fallback, accountability, and Dip UI tests
- Durable job idempotency/claim/lease/retry/scanner tests, login throttling/audit tests, acknowledgement-without-execution tests, and ten-page workspace tests
- Handler registry/coordinator, analysis dependency/blocking, retry idempotency, and full import-to-recommendation EOD vertical integration tests
- Evidence readiness, position sizing, conflict resolution, ETF/classification policy, GOOGL/DRAM/DXYZ analysis, formal recommendation persistence, and position report contract tests
- Analyst-first RTL tests for the Executive Dashboard, Portfolio Import, Portfolio List, Position Detail, and Classification Modal; Playwright covers login, empty portfolio, CSV upload/preview/confirm, queued/ready analysis, recommendation acknowledgement, and opening a position
- Complete target-portfolio vertical acceptance covering GOOGL, DRAM, DXYZ, MSFT, QQQM, VGT, NOK, AAOI, VOO, CSIQ, TSLA, SNDK, NVDA, SPAXX cash, and unvested AMZN compensation; the test invokes the durable pipeline and formal acknowledgement APIs without inserting recommendation outcomes
- T00 capital tests prove 80k invested + 20k emergency cash yields 80k investable/100k liquid, an 8k position is 10%, a 20k cluster is 25%, snapshots are idempotent/versioned, and target underweight alone cannot produce ADD
- T01 tests prove completed-session freshness, stale-session blocking, healthy decision prices without bid/ask, provider capability declarations, and explicit non-retryable plan-limit failure propagation
- T02 tests prove period resolution, SEC concept mapping, restatement precedence, derived financial metrics, explainable health states, and fail-closed missing-fact behavior
- T03 tests prove multi-window revisions, normalized dispersion, strongly-negative Quality ADD blocking, missing-estimate confidence reduction, and module-boundary integrity
- T04 tests prove historical valuation percentiles, insufficient-history confidence gates, deep-discount starter rules, broken-company exclusion, and that underweight positions cannot buy when expensive or revision-negative
- Backtest next-open/slippage/gap, split/dividend, ETF lifecycle, Core/Active, quantity, bias, walk-forward/OOS, decision-metric, V8 scope/idempotency, and report UI tests
- CI dependency/secret/image gates, Playwright navigation, migration-mode exit, runtime smoke, and V8 backup/restore verification

## Known Issues

- Local development credentials are placeholders and must be replaced outside localhost.
- Local runtime starts with seeded SPY/QQQ instruments but no fetched observations, so data health correctly reports `EMPTY` until ingestion runs.
- Local runtime has no synthesized regime/drawdown rows; the context API correctly reports `EMPTY` until evidence calculation runs.
- Production startup requires a real Alpha Vantage API key and a declared SEC organization/contact User-Agent; local fixtures remain intentionally isolated by profile.
- Production analysis remains non-ready until real provider credentials, imported holdings, and sufficient completed market evidence are available; the pipeline fails or reports partial evidence instead of synthesizing readiness.
- Local runtime starts without private account/position data or synthesized recommendations; authenticated portfolio APIs correctly return empty collections until data is imported.
- Production recommendation generation remains dormant until real, quality-gated provider and portfolio data are configured; an order lifecycle is intentionally absent.
- Strategy `2.0.0-draft` remains unpublished and local credentials remain placeholders.
- T05-T15 transformation capabilities remain pending and must be delivered in playbook order; no claim of full Analyst Intelligence V2 readiness is made after T04.

## Next Step

Implement Transformation Phase T05 in playbook order without beginning the pre-T08 visual redesign.
