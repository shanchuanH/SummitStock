# PROJECT_STATE

## Current Phase

Hardening Gate 0 completed on 2026-08-10 in commit `449977b`: the reproducible merge gates now include current pinned GitHub Actions, executable Maven wrapper metadata, Spotless UNIX line endings, dependency review, Gitleaks, pnpm audit, Docker build, frontend type/lint/unit/build/API/E2E gates, and a documented branch-protection contract. Local verification passed; remote GitHub branch-protection enforcement remains explicitly unverified because no authenticated GitHub session or CLI credential is available in this environment.

Hardening A1 implements canonical mark-to-market as an append-only `position_mark_snapshot` plus deterministic latest-mark view. Completed adjusted daily closes now drive capital, weights, cluster contribution, drawdown/equity, holding evidence, earnings weights, portfolio APIs, and executive brief metrics; imported broker `market_value` remains provenance evidence only. Missing/stale marks propagate non-healthy capital quality and block exact sizing or drawdown rather than silently falling back. The analysis pipeline captures marks before portfolio-dependent computation. Regression coverage proves price revaluation without re-import, preservation of broker evidence, missing-mark fail-closed behavior, current-weight movement across a hard-cap boundary, and marked portfolio equity.

Hardening A2 replaces global magic-rank conflict selection with explicit `DecisionChannel` and `EvidenceDependency` semantics. Independently established EXIT, REDUCE_HALF, and TRIM candidates resolve in a risk-reduction channel before new-risk eligibility or maintenance candidates; EXIT is strongest, followed by REDUCE_HALF and TRIM. Emergency reserve, pain-line, DO_NOT_ADD, or WAIT_FOR_DATA candidates can block new exposure but cannot suppress an already validated reduction. The seven-case precedence matrix and Quality/Tactical/Speculative integration cases pass.

Hardening A3 makes broad-US and technology core targets aggregate sleeve targets instead of per-instrument targets. `PortfolioAllocationService` computes and snapshots BROAD_CORE, TECH_CORE, QUALITY, THEMATIC, TACTICAL, SPECULATIVE, and CASH_RESERVE allocations from canonical marks and investable capital. Ordinary Core ETF gap filling is restricted to the configured primary instrument (`VOO` or `QQQM`); alternate ETFs contribute to the sleeve but do not independently fill its gap. Tests prove QQQM 8% + VGT 7% and VOO 20% + SPY 15% produce no buy, while a 10% technology sleeve exposes only the primary QQQM to the 5% aggregate gap.

Hardening A4 replaces the placeholder ETF-dip row count with `EtfDipEventService`, which assembles canonical portfolio/benchmark/indicator/reserve evidence, evaluates the shared `EtfDipEngine` using Strategy V2 thresholds and tranches, and appends auditable event snapshots with trigger codes, tranche/cooldown state, reserve projection, quality, strategy hash, rules, and checksum. UPDATE_DIP_EVENTS now runs before holding analysis; the formal Core ETF engine can deploy only from a non-expired `READY_FOR_TRANCHE_n` event and never reimplements drawdown qualification. Dip sizing uses tactical reserve × configured tranche percentage, capped by deployable cash and remaining aggregate sleeve capacity. A drawdown state without a canonical event cannot deploy, and the full real-portfolio pipeline reaches recommendation generation with the new ordering.

Hardening A5 establishes append-only `risk_cluster_snapshot` as the canonical cluster-risk source. `ClusterRiskService` sums each cluster member's latest open-risk amount and divides once by investable assets, preserving member count, evidence quality, strategy version/hash, data-as-of, and checksum. The deprecated per-position `cluster_risk_fraction` is zeroed and no longer consumed. Holding evidence, portfolio constraints, position sizing, Position Report, and Executive Brief now converge on the canonical snapshot; integration coverage proves a 240 + 200 risk cluster over 80,000 investable assets is 440 / 0.55%, and a canonical 1.00% cluster blocks new risk above the 0.75% cap.

Hardening A6 separates descriptive earnings event risk from the resulting policy action end to end. `HoldingEvidence.EarningsEvent` now carries both canonical `eventRisk` and independent `policyAction`; the assembler reads `earnings_risk_snapshot.event_risk` rather than aliasing `action` into the risk namespace. Quality, Tactical, and Speculative engines judge only HIGH/EXTREME event risk, while policy action remains auditable supporting evidence. Unit coverage proves EXTREME plus REDUCE_HALF produces the required Tactical and Speculative reductions and an oversized Quality trim; MySQL integration coverage proves the two database columns retain their distinct meanings.

Hardening B1 completes formal provider coverage for Market Data, SEC Fundamentals, Estimates, Earnings Calendar, and Macro. Alpha Vantage estimates and calendar CSV plus FRED macro observations now normalize into existing provider-neutral contracts; every provider owns explicit URL, credential, rate interval, timeout, and retry configuration. Production startup fails closed for fake/unavailable providers or missing credentials unless `ALLOW_PARTIAL_PRODUCTION=true`, in which case data health explicitly reports PARTIAL. Market, fundamental, estimate, calendar, and macro collection isolate per-instrument failures into affected identifiers and warnings; SPY and QQQ failing together remains a shared critical-dataset failure. Provider-format contracts and isolation/startup tests pass without live credentials.

Hardening B2 replaces portfolio-derived breadth with canonical point-in-time SP500 and NASDAQ100 universes. V26 adds dated constituent membership and append-only breadth snapshots containing 50/200-day participation, advance/decline, member counts, expected-universe coverage, quality, timestamps, and checksums. `BreadthService` refuses to label incomplete constituent coverage healthy, and both market regime and portfolio drawdown consume only the two-universe canonical snapshot. MySQL integration coverage proves an instrument with full price history but expired/non-member status cannot change breadth, while two valid members produce the expected 50% participation independently of current holdings.

Transformation Phases T00-T15 completed on 2026-08-09 from exact audited baseline `f015a3ed665a9bef38fc03beaea255ee2f21b7ea` on branch `transformation/analyst-intelligence-v2`. The Analyst Intelligence V2 transformation playbook is fully implemented in order.

Transformation baseline: the first backend run failed only because Docker Desktop was not running (`ENVIRONMENT`, 40 Testcontainers initialization errors). After Docker was started, the unchanged baseline passed 108 backend tests (8 quant, 23 strategy, 6 backtest, 71 backend), 27 frontend tests, frontend lint, and production build. The existing frontend bundle-size warning remains non-blocking.

T00 introduced immutable Strategy V2 (`2.0.0-draft`) with configurable resource location, publish state, SHA-256 config hash, fail-closed emergency exclusion, and an explicit `underweightAloneCanTriggerAdd=false` guardrail. V14 adds append-only, owner-scoped capital snapshots. `CapitalBaseService` now derives invested tradable assets, tracked/emergency/deployable cash, investable assets, total liquid assets, and unvested compensation from durable records. Position/cluster weights and risk snapshots use investable assets; total liquid assets remain available for reporting. The current holding engine can no longer emit ADD solely because a position is below target. Targeted capital/weight/guardrail tests and the full 112-test backend suite pass.

T01 separates completed-session decision-price evidence from execution-liquidity evidence. A missing bid/ask now leaves a valid EOD decision price `HEALTHY` while execution liquidity is explicitly `MISSING`; stale completed sessions remain blocked. The US-equity trading calendar drives freshness instead of elapsed wall-clock hours. Market and fundamentals providers expose typed capabilities, provider failures retain the exact rate-limit/plan-limit/timeout/auth/malformed/unavailable taxonomy, and plan limits propagate as non-retryable pipeline failures rather than becoming no-action conclusions. V15 persists quote evidence dimensions and provider capability snapshots. The full 117-test Maven suite passes (8 quant, 23 strategy, 6 backtest, 80 backend).

T02 connects SEC submissions/companyfacts to the durable analysis chain through `CHECK_FILINGS`, `COLLECT_FUNDAMENTALS`, `NORMALIZE_FINANCIALS`, and `COMPUTE_FINANCIAL_HEALTH`. V16 stores canonical periods, provenance-preserving facts, base/derived metric snapshots, and explainable health dimensions. Restatements resolve by latest filing, concept mappings and health thresholds are configuration backed, new filings expire derived valuation/recommendation evidence, and missing facts can never default to healthy. Canonical health now feeds holding and brief readiness while legacy evidence remains readable during migration. The full 123-test Maven suite passes (8 quant, 23 strategy, 6 backtest, 86 backend).

T03 adds a vendor-neutral `EstimateDataProvider`, canonical EPS/revenue estimate history, and 7/30/90-day revision intelligence. V17 persists observations and explainable revision snapshots with analyst coverage, dispersion, quality, and checksums. The durable chain now runs `COLLECT_ESTIMATES` then `COMPUTE_REVISIONS`; no configured external estimate source yields explicit `MISSING` evidence. Strongly negative revisions block Quality ADD/starter actions, normal ADD requires at least FLAT, and missing/partial estimates lower confidence. The full 127-test Maven suite passes (8 quant, 23 strategy, 6 backtest, 90 backend).

T04 replaces target-gap buying with canonical valuation evidence and quality-gated decisions. V18 stores trailing/forward P/E, EV/Sales, FCF yield, Price/Sales, market cap, separate 3Y/5Y own-history percentiles, growth-adjusted valuation, confidence, and durable starter-event state. `COMPUTE_VALUATION` now follows revisions. High-confidence deep discount requires at least 252 observations, healthy company evidence, and no strongly-negative revision. Quality `STARTER_BUY` and normal `ADD` enforce portfolio, thesis, stop, valuation, revision, trend, and capacity gates; starter sizing is 25% and underweight alone cannot buy. The full 133-test Maven suite passes (8 quant, 23 strategy, 6 backtest, 96 backend).

T05 adds a provider-neutral 90-day earnings calendar, canonical earnings events, per-event post-release reaction snapshots, and recent 8–12 event statistics for absolute moves, gap tails, volume shock, and pre-event runup. V18.1 extends durable earnings risk with LOW/MEDIUM/HIGH/EXTREME states. Quality holdings can retain a core position, Tactical positions below 1R reduce only under high event risk, binary Speculative positions exit, and Thematic ETFs never inherit single-company earnings semantics. The main pipeline now collects the calendar and computes earnings risk; `POST_EARNINGS_REANALYSIS` refreshes fundamentals, estimates, reaction, thesis, valuation, holding analysis, and recommendations without allowing transcript summaries to create actions. The full 138-test Maven suite passes (8 quant, 23 strategy, 6 backtest, 101 backend).

T06 adds durable eight-state price/trend evidence using SMA20/50/200, RSI, MACD, rolling-high drawdown, benchmark-relative strength, and six independent reversal signals; `REVERSAL_CONFIRMED` requires at least two. V18.2 stores the state and confirmation count, and Quality ADD now consumes it. The EOD pipeline no longer owns stop formulas: it supplies completed-bar structure/indicator evidence to strategy-core `StopEngine`, which owns configurable ATR volatility distance, exact structure/soft/catastrophic formulas, Core ETF exemption, and monotonic live stops. Formal EOD risk uses completed-bar close while intraday quotes remain isolated for catastrophic alerts. Speculative time-stop policy remains no-progress based and averaging down remains prohibited. The full 145-test Maven suite passes (9 quant, 29 strategy, 6 backtest, 101 backend), including the target-portfolio vertical acceptance.

T07 adds a vendor-neutral macro provider contract for VIXCLS, BAMLH0A0HYM2, DGS10, DGS2, and FEDFUNDS. V18.3 persists macro observations and volatility, credit, rate, curve, and stress-resilience factor snapshots. The 40/20/20/20 regime now uses VIX percentile, HY spread percentile, and realized volatility rather than a placeholder; unavailable external macro data remains explicit and falls back conservatively to realized volatility. Drawdown attribution now distinguishes MARKET_DRIVEN, POSITION_SPECIFIC, CLUSTER_SPECIFIC, MIXED, and UNKNOWN using benchmark drawdown, breadth, stress, largest-position contribution, and durable cluster membership. A market-driven 15% drawdown can deploy a qualified ETF tranche when emergency cash is healthy; concentrated position or cluster losses cannot trigger blind dip buying. The full 151-test Maven suite passes (9 quant, 33 strategy, 6 backtest, 103 backend).

T08 replaces the monolithic holding rule list with classification-routed Quality Stock, Core ETF, Thematic ETF, Tactical Stock, and Speculative decision engines plus a portfolio constraint engine. The resolver enforces the playbook's eleven-level ordering from data eligibility through hold/watch, records winning and suppressed rules, and V18.4 adds durable evidence references to analysis and recommendation audit records. Quality ownership, valuation, revision, price, event, cap, and risk gates now resolve independently; Core ETF market-driven dip deployment, Thematic fund-only evidence, and Speculative no-wait-for-recovery semantics are explicit. The complete ten-scenario decision matrix and full 161-test Maven suite pass (9 quant, 33 strategy, 6 backtest, 113 backend).

T09 rebuilds deterministic quantities on investable assets and deployable cash. BUY/ADD sizing takes the minimum of per-trade risk, hard-weight capacity, deployable cash, and remaining cluster-risk amount divided by per-share risk; emergency cash is never exposed and starter sizing applies the configured fraction to every capacity. TRIM, REDUCE_HALF, and EXIT use target/hard-cap excess or current quantity instead of reversing the buy formula. Exact quantities now fail closed for stale prices, required missing stops, partial capital, stale/impaired risk, unconfirmed classification, and open provider hard errors. The full 166-test Maven suite passes (9 quant, 33 strategy, 6 backtest, 118 backend).

T10 adds a provider-neutral analyst narration boundary whose input contains the already-final action and grounded evidence but no authority to decide an action, calculate quantity, set a stop, or override risk. V19 persists source/model attribution, structured narrative sections, input/output checksums, and validation status per recommendation. The fact validator rejects unsupported percentages, newly invented target prices, and analyst-consensus claims without analyst evidence; unavailable or invalid model output deterministically falls back without blocking recommendation generation. Position reports expose the persisted narrative while retaining deterministic action and audit fields. The full 170-test Maven suite passes (9 quant, 33 strategy, 6 backtest, 122 backend).

T11-T13 deliver the unified Executive Terminal, backend-owned Executive Brief V2 command-center fields, and quota-aware provider collection. The UI exposes honest readiness and missing evidence without inventing precision. V20 records daily provider usage by operation and P0-P4 priority; owned-holding quotes, filings, fundamentals, and estimates survive quota pressure while optional scanning pauses before exhaustion.

T14 adds point-in-time gates for filings (`filed_at`), estimates (`data_as_of`), and earnings results (event availability), plus expanding non-overlapping OOS calibration. Portfolio metrics now include CAGR, maximum drawdown, annualized volatility, turnover, Average R, tail loss, time underwater, exposure, and SPY/QQQ-relative performance. V21 ties a strategy draft to a matching config-hash, successful bias-clear OOS backtest artifact and named human approval before publication. Backtest-core passes 10 tests and the MySQL strategy-governance integration test passes.

T15 replaces permissive vertical acceptance with an exact `ANALYSIS_READY` gate. Its isolated fixture executes Import, Resolve, provider-backed market/filing/fact/event evidence, financial normalization, estimates, valuation, regime, drawdown, analysis, decision, sizing, brief, position report, and acknowledgement without inserting recommendation outcomes. The fixture protects $20,000 emergency cash, supplies explicit fund-profile input evidence, cleans global evidence before and after execution, and exposes no exact new-buy quantity when estimates are missing. It also fixed earnings-event `DATETIME` mapping, classification-routed earnings collection for Speculative positions, and actionable analysis without requiring a pre-existing user thesis. The shared-context Maven suite now passes 180 tests (9 quant, 33 strategy, 10 backtest, 128 backend); frontend passes 13 files/27 tests, lint, typecheck, generated-client build, and production build.

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

- Flyway head: `V21__strategy_release_governance.sql`
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
- Daily provider usage tracks request/success/failure counts by provider, operation, UTC day, and P0-P4 priority
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
- T05 tests prove recent earnings-reaction statistics, Quality core-hold policy, binary Speculative exits, Thematic ETF exclusion, and the ordered post-earnings evidence refresh chain
- T06 tests prove StopEngine policy parity, monotonic live stops, exact ATR soft/catastrophic levels, Core ETF exemption, two-signal reversal confirmation, relative strength, and the full target-portfolio stop/risk chain
- T07 tests prove macro-factor composition, credit-tail stress, regime hard overrides, market-driven 15% ETF deployment, position-specific dip exclusion, and cluster-specific attribution
- T08 tests prove underweight-only restraint, broken-company exits, valuation blocks, deep-discount starters, confirmed Quality adds, hard-cap and pain-line precedence, market-driven Core ETF dip deployment, Speculative stop precedence, and Thematic ETF exclusion of company earnings
- T09 tests prove investable-asset risk/weight sizing, emergency-cash exclusion, starter scaling, cluster risk-amount capacity, stale/impaired evidence precision blocking, and action-specific trim quantities
- T10 tests prove deterministic fallback, validator rejection of unsupported percentages/target prices/analyst consensus, durable per-recommendation narrative provenance, and continued operation without model credentials
- T11 tests prove the unified Executive Terminal renders honest empty/error/readiness states, caps MUST_ACT at three, preserves row-scoped classification, renders twelve evidence modules, and never invents missing financial or sizing precision
- T11 frontend quality gate: 13 Vitest files / 27 tests pass using the stable fork pool; ESLint and the Vite production build pass
- T12 contract tests prove Executive Brief V2 exposes market, capital, portfolio, opportunities, blocked, next events, and readiness as backend-owned fields; data blockers are separated from Watch
- T12 UI tests (7 focused Vitest cases), generated-client type checks, ESLint, and the Vite production build pass
- T13 tests prove P0/P1 owned-holding evidence survives quota pressure, P3/P4 optional work pauses at 90% utilization, operations map to explicit P0-P4 priorities, and real provider requests persist daily usage and outcomes
- T14 tests prove filing/estimate/earnings point-in-time availability, expanding OOS folds, all required portfolio calibration metrics, and mandatory matching backtest artifact plus approval before strategy publication
- T15 tests prove exact `ANALYSIS_READY`, GOOGL normal-max restraint, deep-discount starter, confirmed reversal add, cheap/deteriorating rejection, QQQM market-drawdown deployment, DXYZ stop exit, missing-estimate quantity blocking, provider-evidence isolation, full report delivery, and acknowledgement without execution
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

## Next Step

Configure production provider credentials, run the documented deployment smoke checks, and publish a strategy version only after the T14 governance approval flow is satisfied.

## Hardening Gate 0 — 2026-08-10

- Git records `mvnw` as executable so Linux CI can invoke the wrapper directly.
- GitHub Actions now use the current supported Node 24 action generations: checkout v7, setup-java v5, setup-node v6, pnpm setup v6, dependency review v5, and gitleaks action v3.
- Spotless explicitly enforces UNIX line endings, making format checks reproducible across Windows workstations and Linux CI.
- Existing Spotless violations in backtest/core and backend sources were mechanically normalized; no investment behavior or thresholds changed.
- Main branch protection requirements and an operator verification procedure are documented in `docs/runbooks/branch-protection.md`.
- Backend verification baseline: 9 quant, 33 strategy, 10 backtest, and 128 backend tests pass.
- Frontend lint, typecheck, 27 Vitest tests, and production build pass. API generation completed; the first combined gate timed out while Git inspected pre-staged line-ending metadata and is rerun after this Gate 0 commit.
- The workspace overrides transitive `js-yaml` to patched version 4.3.1; `pnpm audit --audit-level high` reports no known vulnerabilities.
- Playwright's two browser journeys pass after bringing the stale Executive Brief fixture and acknowledgement interaction in line with the current generated API/UI contract.
- Compose validation, the backend Docker image build, and a Gitleaks v8.30.1 scan of 25 commits pass with no leaks found.
- GitHub `main` branch protection remains external-state NOT VERIFIED: this workstation has no `gh` CLI and the available browser session is not authenticated to repository settings. The required settings and verification drill are documented for application once authenticated administration is available.

## Hardening B3 - 2026-08-10

- Removed the decision layer's fixed 36-hour freshness constant. All six thresholds now load from the versioned strategy definition: EOD price sessions, financial-quarter days, estimates days, earnings-calendar days, ETF-profile days, and macro-daily days.
- Price freshness is evaluated in completed US-equity trading sessions; financials, valuation, estimates, earnings calendars, ETF profiles, stops, market regime, and portfolio risk retain and evaluate their own `data_as_of` values.
- Readiness now follows classification-specific evidence contracts: Quality requires price/financial health/valuation/revisions/next earnings/portfolio risk; Tactical requires price/stop/thesis/next event/portfolio risk; ETF requires price/fund profile/liquidity/overlap/regime/risk.
- A fresh quote can no longer conceal a stale fundamental snapshot. The regression case uses a five-minute-old quote and 250-day-old fundamentals against the configured 140-day limit and returns `STALE`, preventing precise new-capital sizing.
- Integration fixtures were completed with actual revision, risk, regime, and thesis evidence so existing vertical scenarios satisfy the same production rules rather than bypassing them.
- Verification: Maven reactor passes 9 quant, 33 strategy, 10 backtest, and 157 backend tests. Frontend ESLint, typecheck, 13 Vitest files / 27 tests, and production build pass.

## Hardening B4 - 2026-08-10

- SEC company-fact ingestion now preserves provider fiscal year (`fy`) and fiscal period (`fp`) through the provider model, raw financial fact storage, and normalization resolver.
- Migration V27 adds provider fiscal metadata to `financial_fact_observation` and a comparable-period index to `financial_period`.
- Canonical periods are explicit `FY`, `Q1`, `Q2`, `Q3`, or `Q4`. Quarterly periods without provider `fp` fail closed instead of inferring a fiscal quarter from the calendar month.
- YoY and three-year comparisons select the same canonical fiscal period from the required prior fiscal year, not an exact `endDate.minusYears(...)`, so 52/53-week issuers remain comparable.
- Tests cover a retailer Q1 ending in May, missing fiscal-period metadata, a one-day-shifted 53-week comparison, and official SEC `fy/fp` parsing.
- Verification: Maven reactor passes 9 quant, 33 strategy, 10 backtest, and 160 backend tests. Frontend ESLint, typecheck, 13 Vitest files / 27 tests, and production build pass.

## Hardening B5 - 2026-08-10

- Financial concepts now pass explicit unit and sign contracts before normalization: monetary facts require USD, diluted shares require `shares`, diluted EPS requires `USD/shares`, and balance-sheet values such as debt cannot be negative.
- SEC ingestion collects all relevant debt concepts. `TOTAL_DEBT` uses a reported aggregate when present and never also sums its components; otherwise it sums distinct short-term, current, and non-current components while treating alternative current-debt concepts as mutually exclusive.
- Migration V28 adds `source_concepts`, `mapping_version`, and `aggregation_method` to every financial metric snapshot, with safe legacy backfill before enforcing non-null constraints.
- Raw/derived metric provenance uses versioned identifiers and records `PREFERRED_AGGREGATE`, `SUM_DISTINCT_COMPONENTS`, `SINGLE_CONCEPT`, or `DERIVED_FORMULA` as appropriate.
- Filing-index rows no longer infer a fiscal quarter from calendar month; later company-fact normalization supplies provider fiscal metadata on duplicate-period reconciliation.
- Tests prove aggregate debt is not double counted, distinct components are summed once, alternate current components are mutually exclusive, and invalid units/negative debt fail closed. The complete 13-position vertical pipeline remains `ANALYSIS_READY` with correctly typed fixture facts.
- Verification: Maven reactor passes 9 quant, 33 strategy, 10 backtest, and 162 backend tests. Frontend ESLint, typecheck, 13 Vitest files / 27 tests pass; production build has a clean zero exit code.

## Hardening B6 - 2026-08-10

- A single `TradingCalendar` now owns US-equity session membership, session arithmetic, open/close instants, early closes, completed-session selection, and a versioned calendar identity for XNYS/XNAS semantics.
- The rules-based calendar covers observed exchange holidays and algorithmic Good Friday without a handwritten annual date list. It also models the day after Thanksgiving, applicable July 3 sessions, and applicable December 24 sessions as 13:00 Eastern early closes.
- Session boundaries are calculated in `America/New_York`, preserving the exact UTC shift across daylight-saving transitions and preventing an in-progress or holiday bar from being treated as completed.
- Analysis freshness and ETF dip cooldown/expiry logic now use the same centralized session arithmetic instead of maintaining separate trading-day loops.
- Earnings reaction windows now discard non-session bars and apply event timing against exchange sessions. `AFTER_CLOSE` correctly uses the event-session close as the pre-event anchor and starts the reaction on the next trading session.
- Tests cover Good Friday, observed holidays, early-close completion boundaries, daylight-saving UTC offsets, shared session arithmetic, calendar versioning, and an after-close earnings event spanning Good Friday.
- Verification: Maven reactor passes 9 quant, 33 strategy, 10 backtest, and 167 backend tests. Frontend ESLint, typecheck, 13 Vitest files / 27 tests, and production build pass.

## Hardening C1 - 2026-08-10

- Formal recommendation generation now fails closed before expiring or inserting recommendations unless the runtime YAML version exists in `strategy_version`, its byte-level config hash matches, and the database release status is `PUBLISHED`.
- `PORTFOLIO_ALLOW_DRAFT_STRATEGY` defaults to `false`. The explicit override permits a YAML `DRAFT` runtime only when the matching database row is also `DRAFT` with the same hash, or when the local draft has not yet been registered; test fixtures opt in through the test profile.
- Runtime strategy state is exposed through the version contract with config hash, database publish state, production eligibility, and draft-override status. The generated TypeScript client was refreshed from that contract.
- Every application route is wrapped by a shared strategy-status banner. A draft override is visibly labelled `DRAFT STRATEGY / NOT PRODUCTION`; an unverified runtime without an override is labelled blocked and states that formal ACTIVE recommendations are disabled.
- Tests prove missing, DRAFT, hash-mismatched, and exact PUBLISHED release behavior; the existing recommendation integration test proves the explicit test override remains functional. UI tests prove draft warning visibility and suppression for a verified production strategy.
- Verification: Maven reactor passes 9 quant, 33 strategy, 10 backtest, and 170 backend tests. Frontend ESLint, typecheck, 14 Vitest files / 29 tests, generated-client build, and production Vite build pass.

## Hardening C2 - 2026-08-10

- `StrategyDefinition` and its loader now retain every decision-affecting V2 YAML leaf, including all five drawdown thresholds, exact-quantity evidence switches, risk-over-tax precedence, tactical-reserve range, manual-execution invariant, benchmark identities, deep-discount starter enablement, speculative averaging policy, and all freshness values.
- A consumed-key registry covers every decision key while `profile.broker` is the sole explicitly classified non-decision metadata leaf. `StrategyConfigParityTest` compares the exact flattened YAML leaf set to that registry, so an unparsed or unclassified future key fails CI.
- The formal runtime now passes configured drawdown thresholds into `DrawdownEngine`, configured price/risk requirements into exact sizing, configured risk precedence into conflict resolution, and the configured deep-discount switch into Quality starter decisions.
- Existing strategy-core callers retain the V2 defaults through compatibility entry points; the backend runtime uses the loaded strategy values. A focused test moves the thresholds away from the defaults and proves classification follows configuration rather than embedded percentages.
- Verification: Maven reactor passes 9 quant, 34 strategy, 10 backtest, and 172 backend tests. Frontend ESLint, typecheck, 14 Vitest files / 29 tests, generated-client build, and production Vite build pass.

## Hardening C3 - 2026-08-10

- Formal `DecisionContext` now carries persisted behavioral evidence: last acknowledged decision/add, averaging-down state, thesis improvement, cost-basis anchoring, holding trading sessions, thesis progress, idea cooldown, and the decision timestamp.
- A production `BehavioralFirewall` participates in the same candidate set and conflict resolver as portfolio and asset rules. Active cooling, unsubstantiated averaging down, and cost-basis anchoring produce `DO_NOT_ADD`; risk-reduction candidates retain precedence and cannot be suppressed by cooling.
- Speculative positions now use a versioned 60-trading-session time stop from strategy YAML. Expiry without persisted thesis progress produces a formal `EXIT`, while documented progress prevents it.
- Migration V29 adds thesis-progress evidence and an `investment_idea` workflow with `idea_created_at`, `cooldown_until`, and constrained `source_type`; social/watchlist ideas cannot enter a buy candidate before cooldown expiry.
- Recommendation narration rejects cost basis or break-even language as an invalid decision anchor even if the underlying position facts include cost basis.
- Tests prove all behavioral rules directly and through real MySQL formal recommendation generation, including that an active cooldown cannot suppress a speculative time-stop exit.
- Verification: Maven reactor passes 9 quant, 34 strategy, 10 backtest, and 179 backend tests. Frontend ESLint, typecheck, 14 Vitest files / 29 tests, generated-client build, and production Vite build pass.

## Hardening C4 - 2026-08-10

- Backtest bias status is no longer caller supplied. `BacktestBiasProofEvaluator` derives `CLEAR` or `BLOCKED` from the training/OOS boundary, point-in-time feature timestamps, completed-bar evidence, and versioned universe, price-adjustment, exchange-calendar, cost-model, and feature-cutoff policies.
- Migration V30 persists those five reproducibility versions plus a machine-readable `bias_proof`; legacy runs are explicitly backfilled as `NOT_EVALUATED` with `LEGACY_UNVERIFIED` provenance and cannot become release evidence.
- `BacktestReportStore` verifies that persisted training/OOS dates match the proof, stores blocked runs rather than hiding them, and exposes the proof provenance through the read-only API contract.
- Strategy approval now requires a successful, system-derived, empty-failure proof with non-legacy versions. Publication revalidates the approved run so post-approval tampering or stale legacy evidence fails closed.
- Tests prove a complete point-in-time proof clears, overlap/future features/incomplete bars block, callers cannot force `CLEAR`, and mutation after human approval prevents publication.
- Verification: Maven reactor passes 9 quant, 34 strategy, 10 backtest, and 183 backend tests. Frontend ESLint, typecheck, 14 Vitest files / 29 tests, generated-client build, and production Vite build pass.

## Hardening D1 - 2026-08-10

- Migration V31 adds a unique UUID fencing token to each worker lease and safely returns any in-flight legacy job to `PENDING` during deployment.
- Claiming a job atomically installs a new owner, token, expiry, and attempt. Heartbeat, success, retry, and permanent failure writes require the same running job, owner, token, and an unexpired lease; a stale writer raises `LeaseLostException` and its transaction is rolled back.
- The worker coordinator renews active leases every 30 seconds against a five-minute lease, cancels the heartbeat when execution finishes, and never publishes orchestration success/failure from a fenced worker.
- Expired-lease recovery marks the abandoned attempt `FAILED/LEASE_EXPIRED` before making the job claimable, preserving an auditable attempt history.
- The required A/B race test proves that after A expires and B reclaims, A cannot heartbeat or complete, B exclusively owns the final result, and A's attempt remains closed as expired.
- Verification: Maven reactor passes 9 quant, 34 strategy, 10 backtest, and 184 backend tests. Frontend ESLint, typecheck, 14 Vitest files / 29 tests, generated-client build, and production Vite build pass.

## Hardening D2 - 2026-08-12

- Quote scheduling now uses the canonical exchange calendar and runs at one-minute slots only during the regular XNYS/XNAS session; weekends, holidays, pre-market, and post-market enqueue no polling jobs.
- Market polling no longer scans every historically active instrument. The canonical tracked set is limited to open positions, benchmark metadata, and explicitly active `WATCHLIST` ideas. Migration V32 separates watchlist activity from behavioral cooldown semantics.
- `ProviderExecutionPolicy` is now the single execution boundary for every production provider HTTP request. It centralizes minimum interval enforcement, bounded retry/backoff, the existing daily quota gate, and durable request journaling.
- Alpha Vantage market/estimate/earnings clients, SEC, and FRED receive distinct policy identities and usage records. Request journal context stores only host and path, so API keys and query secrets are not persisted.
- Tests prove 429 retry through the central policy, quota/journal accounting, secret redaction, production provider contracts, regular-session-only scheduling, exclusion of untracked historical instruments, and inclusion of benchmarks plus active watchlist ideas.
- Verification: Maven reactor passes 9 quant, 34 strategy, 10 backtest, and 187 backend tests. Frontend ESLint, typecheck, 14 Vitest files / 29 tests, generated-client build, and production Vite build pass.

## Hardening D3 - 2026-08-12

- A production-only startup gate rejects blank credentials and all documented placeholder values (`change-before-use`, `change-local-app-password`, and `change-local-root-password`) before the application can serve traffic.
- Production startup also fails when Secure session cookies or forwarded-header handling are disabled, when `local-fixture` is co-activated, or when a fake market/fundamentals provider is selected. The production profile defaults to Secure cookies and framework proxy-header processing.
- Deployment configuration is split into `infra/compose.local.yaml` and `infra/compose.prod.yaml`. Local fixtures and published development ports exist only in the local file.
- Production Compose requires an immutable image reference and explicit secrets, exposes MySQL only on an internal network, publishes no database or backend host port, gives the backend only the application database credential, and makes the API reachable solely through an external reverse-proxy network.
- Local/production runbooks, CI Compose validation, and backup/restore scripts now reference the correct environment-specific Compose file. Both Compose models pass `docker compose config --quiet` with their documented inputs.
- Verification: production security/provider gates pass 9 focused tests; real MySQL session, CSRF, login, and throttling smoke tests pass. Maven reactor passes 9 quant, 34 strategy, 10 backtest, and 190 backend tests. Frontend ESLint, typecheck, 14 Vitest files / 29 tests, generated-client build, and production Vite build pass.

## Hardening D4 - 2026-08-12

- `ExecutiveBrief.confirmedNoAction` is now the backend-owned canonical no-action fact and is required in the OpenAPI contract and generated TypeScript client.
- The flag is true only for `ANALYSIS_READY` with empty must-act, do-not, watch, and blocked queues, healthy full market/fundamental coverage, and no stale, missing, or failed evidence.
- The dashboard no longer derives a calm state from empty arrays. It displays `ANALYSIS PARTIAL`, `WAITING FOR DATA`, or `BLOCKED` for incomplete, waiting, stale, blocked, and failed analysis instead of implying that no action is required.
- Contract tests prove that empty/no-portfolio and ready-with-action responses remain unconfirmed, while a fully covered ready portfolio with every action queue empty is explicitly confirmed.
- Verification: Maven reactor passes 9 quant, 34 strategy, 10 backtest, and 191 backend tests. Frontend ESLint, typecheck, 14 Vitest files / 29 tests, generated-client build, and production Vite build pass.

## Hardening D5 - 2026-08-12

- Pipeline and scheduler names now state their real behavior: thesis inspection is `CHECK_ACTIVE_THESES`; daily recommendation inspection is `COUNT_ACTIVE_RECOMMENDATIONS`; scheduled weekly/monthly counts are `COUNT_VALID_RECOMMENDATIONS` and `COUNT_RECENT_SUCCESSFUL_ANALYSES`.
- `UPDATE_DIP_EVENTS` remains unchanged because it performs the claimed durable dip-event capture side effect.
- Flyway V33 migrates historical analysis-step dependencies and durable job types to the honest names, preserving existing audit records and run topology.
- A semantic contract test prevents the misleading update/generate names from returning, while scheduler tests prove the periodic jobs enqueue the renamed read-only operations.
- Verification: Maven reactor passes 9 quant, 34 strategy, 10 backtest, and 193 backend tests. Frontend ESLint, typecheck, 14 Vitest files / 29 tests, generated-client build, and production Vite build pass.

## Hardening D6 - 2026-08-12

- Flyway V34 links each holding-analysis snapshot to its owning `portfolio_analysis_run`, enforces one canonical snapshot per run/position, and preserves snapshots if an old run is deleted.
- The compute step persists the complete resolved decision and deterministic narrative input as an auditable JSON payload alongside the normalized snapshot columns.
- `GENERATE_RECOMMENDATIONS` now loads the exact snapshots for its run and creates recommendations/narratives from their persisted decisions; it never invokes `analyzeAll()`.
- The post-earnings path also performs holding analysis only once by using the single-pass recommendation entry point.
- The real three-position EOD acceptance test proves exactly three run-linked snapshots and three recommendations that reference those same snapshots.
- Verification: Maven reactor passes 9 quant, 34 strategy, 10 backtest, and 193 backend tests. Frontend ESLint, typecheck, 14 Vitest files / 29 tests, generated-client build, and production Vite build pass.

## Hardening Recommendation Risk Fields - 2026-08-12

- Recommendation risk is no longer written as two unconditional nulls. `riskBeforeFraction` uses the current persisted portfolio planned-risk fraction when healthy risk evidence exists.
- Sizing actions project `riskAfterFraction` from the formal stop, current quote, maximum recommended quantity, and investable equity; risk-reduction actions subtract projected risk and new-risk actions add it.
- Non-sizing actions preserve before/after risk, while unavailable projections remain null with an explicit `riskCalculationReason` such as `PORTFOLIO_RISK_EVIDENCE_UNAVAILABLE` or `PROJECTED_RISK_INPUT_MISSING`.
- Flyway V35 backfills legacy rows with `LEGACY_NOT_CALCULATED`; both recommendation API representations expose the required reason and the generated client reflects it.
- Verification: Maven reactor passes 9 quant, 34 strategy, 10 backtest, and 193 backend tests. Frontend ESLint, typecheck, 14 Vitest files / 29 tests, generated-client build, and production Vite build pass.

## Hardening Tax-Lot Scope - 2026-08-12

- Recommendation APIs now expose a required `taxLotStatus`; no UI or API contract implies that a total-quantity recommendation has been tax optimized.
- Sell-sizing decisions return `TAX_DATA_MISSING` unless persisted tax lots cover the recommended maximum quantity. Covered decisions return `TAX_LOTS_AVAILABLE_NOT_OPTIMIZED`, explicitly preserving the distinction between data availability and optimization.
- Non-sell actions return `NOT_APPLICABLE`; Flyway V36 labels historical recommendations `LEGACY_UNKNOWN` rather than inventing tax evidence.
- Total recommended quantities remain available independently of lot selection, matching the intentionally limited scope of this hardening pass.
- Verification: Maven reactor passes 9 quant, 34 strategy, 10 backtest, and 193 backend tests. Frontend ESLint, typecheck, 14 Vitest files / 29 tests, generated-client build, and production Vite build pass.

## Hardening Position Bucket Consistency - 2026-08-12

- `position.bucket` is confirmed as the strategy sleeve, not an independent user label. Classification confirmation now updates classification and sleeve atomically under the same optimistic version check.
- `CORE_BROAD_ETF`, `CORE_TECH_ETF`, and `CASH_EQUIVALENT` map to `CORE`; quality, thematic, tactical, turnaround, and speculative classifications map to `TACTICAL_OVERLAY`.
- Flyway V37 applies the same deterministic mapping to every existing confirmed position, eliminating historical classification/bucket disagreement.
- API integration tests prove both core and quality classification paths return the synchronized bucket; the complete real-portfolio vertical scenario remains green.
- Verification: Maven reactor passes 9 quant, 34 strategy, 10 backtest, and 194 backend tests. Frontend ESLint, typecheck, 14 Vitest files / 29 tests, generated-client build, and production Vite build pass.

## Modification Manual V1 — Phase 0 — 2026-08-12

- CI is partitioned into deterministic backend, frontend, browser E2E, supply-chain, and container gates, with a stable aggregate `required` check for branch protection.
- Java/JUnit execution is explicitly serial and Playwright uses one CI worker with no server reuse, removing shared MySQL and local-server races from the required path.
- Repository line endings are pinned by `.gitattributes`, fixing clean Windows checkouts that previously failed Spotless before any test executed.
- All production-integrated test fixtures now reference the active `2.0.0-draft` strategy instead of the retired V1 draft; historical ADR text remains unchanged as design history.
- OpenAPI and generated TypeScript artifacts were regenerated with the locked toolchain so the contract-drift gate is reproducible.
- Verification baseline: Maven reactor passes 9 quant, 34 strategy, 10 backtest, and 194 backend tests; frontend ESLint, typecheck, 14 Vitest files / 29 tests, generated-client build, and production Vite build pass.

## Modification Manual V1 — Phase 1 — 2026-08-12

- Valuation and financial-health evidence now use four-quarter TTM fundamentals and nearest-forward annual FY1 estimates; negative earnings no longer produce a misleading trailing P/E, margin deterioration uses the configured percentage-point threshold, and leverage requires complete TTM free-cash-flow evidence.
- Position sizing now applies the minimum of trade-risk, projected total-risk, cluster-risk, sleeve-weight, cash, and liquidity limits. Thematic ETFs receive a non-zero volatility proxy so they participate in portfolio and cluster risk.
- Flyway V38 freezes initial trade-risk fields and adds external cash-flow plus unitized NAV history. Deposits and withdrawals change units rather than strategy return, and drawdown is calculated from cash-flow-adjusted NAV.
- Drawdown attribution now compares peak-date and current position values instead of using current market-value weights as a proxy. Profit-cushion R uses immutable entry and initial-stop evidence.
- Earnings gap percentiles are derived from the actual gap distribution. ETF-dip scoring consumes independent volatility, credit, term-structure, breadth, and trend evidence and uses real time-series confirmation triggers.
- Verification: Maven reactor passes 9 quant, 34 strategy, 10 backtest, and 205 backend tests; frontend ESLint, typecheck, 14 Vitest files / 29 tests, generated-client build, and production Vite build pass.

## Modification Manual V1 — Phase 2 — 2026-08-12

- Strategy V3 (`3.0.0-draft`) is the active runtime strategy. Its allocation targets are Broad 35%, Tech 15%, International 10%, Quality 15%, Thematic 8%, Tactical 5%, Speculative 2%, and Tactical Reserve 10% within an ordered 8–12% range.
- The typed definition now owns allocation, emergency cash, drawdown, risk, liquidity participation, thematic risk proxy, stop multipliers, speculative time stop, cash-flow allocation, ETF-dip, freshness, and decision parameters. Runtime sizing, portfolio-risk snapshots, stop calculation/preview, and cash-flow planning consume those typed values.
- Publication validation fails closed unless allocations sum to 100%, position bounds are ordered, trade risk remains below the absolute cap, cluster risk does not exceed total risk, drawdown thresholds strictly increase, ETF-dip tranches total 100%, Must Act is capped at three, and execution remains manual-only.
- The active production/test fixtures, application defaults, frontend strategy status, and container artifact assertion now reference V3; V1/V2 files remain only as immutable historical strategy artifacts.
- Verification: Maven reactor passes 9 quant, 35 strategy, 10 backtest, and 207 backend tests; frontend ESLint, typecheck, 14 Vitest files / 29 tests, generated-client build, production Vite build, and OpenAPI regeneration pass.

## Modification Manual V1 — Phase 3 — 2026-08-12

- Portfolio preview remains read-only while adding config-driven classification suggestions and reasons. Every imported holding requires an explicit owner-confirmed, non-unknown classification before reconciliation or downstream sizing can begin.
- The onboarding flow now captures emergency-cash location in owner language: held at Fidelity, held at an external bank, split between both, or currently below target. Fidelity-held emergency cash is protected from allocation, and the confirmed setup is stored durably by Flyway V39.
- Confirmation atomically persists classifications and cash setup, reconciles the portfolio without creating assets, then queues the existing analysis pipeline. An owner-scoped status endpoint exposes the eight required stages from durable job state instead of simulated progress.
- The web flow includes classification review, cash setup, explicit confirmation, and resumable analysis progress polling. OpenAPI and generated TypeScript contracts include the expanded onboarding and status models.
- Verification: Maven reactor passes 9 quant, 35 strategy, 10 backtest, and 208 backend tests; frontend ESLint, typecheck, 14 Vitest files / 29 tests, generated-client build, production Vite build, and OpenAPI regeneration pass.

## Modification Manual V1 — Phase 4 — 2026-08-12

- `GET /api/v1/brief/today` is the sole owner-dashboard aggregate. It now returns no more than three cross-priority daily actions, the top three unique risks with explicit meaning and immediate response, and all holdings ordered MUST_ACT, DO_NOT, WATCH, then HOLD.
- Every action includes company, classification, action, priority, confidence, current and target weights, quantity range, estimated amount, primary reasons and risks, data timestamp, expiry, and change conditions. The only controls are full analysis, handled, and defer; no automatic execution path exists.
- Owner portfolio health is limited to investable assets, Emergency Cash, strategy drawdown, Tactical plus Speculative exposure, and conservative data completeness. The no-action message remains gated on READY, full coverage, fresh analysis, and empty action queues.
- Frontend fixtures and generated API contracts cover the expanded aggregate without composing lower-level financial services in React.
- Verification: Maven reactor passes 9 quant, 35 strategy, 10 backtest, and 208 backend tests; backend formatting/build gates pass; frontend ESLint, typecheck, 14 Vitest files / 29 tests, generated-client build, production Vite build, and OpenAPI regeneration pass.

## Modification Manual V1 — Phase 5 — 2026-08-12

- The canonical owner endpoint `GET /api/v1/holdings/{id}/analyst-report` now aggregates the existing deterministic analysis into exactly six layers: system recommendation, portfolio role, fundamentals, valuation, price/risk/earnings, and rationale/risk/change conditions/evidence. The prior position report path remains compatible.
- Classification-specific Strategy V3 target, normal maximum, and hard maximum are included in portfolio context. The report explicitly separates an attractive security from available portfolio capacity, so a cheap holding at its hard maximum cannot be presented as an add.
- Rule IDs, evidence references, strategy version, configuration hash, data quality, and timestamps remain behind the Evidence Drawer; missing six-layer evidence fails closed rather than rendering a partial or fabricated report.
- GOOGL quality-stock, DRAM thematic-ETF, and DXYZ speculative fixtures verify distinct 15%, 10%, and 2% hard limits and the complete six-layer contract. The owner page leads with the conclusion and renders six modules rather than an engineering-first chart wall.
- Verification: Maven reactor passes 9 quant, 35 strategy, 10 backtest, and 209 backend tests; frontend ESLint, typecheck, 14 Vitest files / 29 tests, generated-client build, production Vite build, and OpenAPI regeneration pass.

## Modification Manual V1 — Phase 6 — 2026-08-12

- Owner-facing readiness now presents conservative completeness as a plain-language percentage alongside market and fundamental coverage. The information hierarchy remains action, reason, owner data, then technical evidence.
- API failures now use stable problem details for not-found, analysis-not-ready, owner-input-required, and dependency-unavailable cases, including a request ID, retry semantics, and a concrete next action. The web request layer preserves those semantics instead of reducing every failure to a status number.
- Recommendation acknowledgement remains idempotent and explicitly never submits an execution. Dashboard controls are limited to full analysis, handled, and defer; Evidence Drawer content remains collapsed by default.
- Browser acceptance runs the owner brief and six-layer holding report in both desktop Chrome and a Pixel 7 mobile viewport, verifies no horizontal page overflow, validates the completeness badge and evidence drawer, and asserts that no trading or order button exists.
- Verification: Maven reactor passes 9 quant, 35 strategy, 10 backtest, and 210 backend tests; frontend ESLint, typecheck, 14 Vitest files / 29 tests, generated-client and production builds pass; Playwright passes 4/4 desktop and mobile journeys.

## Modification Manual V1 — Phase 7 — 2026-08-12

- Regime, Backtest, Thesis, Journal, and Data Health now live behind one protected Advanced Research hub, while the five-item owner navigation and Executive Brief remain the primary workflow.
- Canonical advanced routes are restored and linked from Settings without exposing worker, strategy-release, or data-health internals as prerequisites for daily decisions.
- Owner-facing onboarding copy now consistently explains classification confirmation, Emergency Cash, reconciliation, and analysis progress in plain Chinese. The post-preview steps use an explicit responsive flow so cash confirmation and analysis submission remain independently operable on narrow screens.
- Final browser acceptance covers Fidelity preview recognition of a stock, ETF, and cash; explicit classification and Emergency Cash confirmation; all eight analysis stages; no more than three priority actions; and a holding report that answers sizing, valuation, earnings-risk, evidence, uncertainty, and change-condition questions.
- The OpenAPI artifact is normalized by the locked generator with no semantic contract change, eliminating formatting-only drift in the required contract gate.
- Verification: Maven reactor passes 9 quant, 35 strategy, 10 backtest, and 218 backend tests; frontend ESLint, typecheck, 15 Vitest files / 30 tests, generated-client and production builds pass; Playwright passes 6/6 desktop and mobile journeys; local Docker Compose configuration and whitespace checks pass.

## User-Friendly Strategy Manual — Baseline and UI-0 — 2026-08-13

- Baseline is the exact audited `main` commit `26bbb0bc28d7eeafb1f7e8beac7abc89cd015685`; the feature branch is `modification/user-friendly-strategy-v1`. The clean baseline passes 9 quant, 35 strategy, 10 backtest, and 210 backend tests, plus 15 frontend files / 30 tests, production build, generated API drift, and 6 desktop/mobile browser journeys.
- A single presentation layer now owns exhaustive owner-facing action, priority, classification, confidence, readiness, reason, number, and date language. Unknown future action values fail safe to “暂不操作” and are never exposed as raw UI copy.
- The presentation contract test reads the deterministic backend `RecommendationAction` enum and fails whenever a backend action lacks a frontend mapping. Dashboard, holdings, holding report, opportunities, review, and ETF-dip history now consume the shared action language instead of local maps.
- The holding hero now reads the absolute position limit from `layers.portfolioRole.hardMaxWeight`, eliminating the former speculative-only hard-limit source. Missing numbers render as unavailable rather than zero.
- Verification: frontend ESLint and TypeScript pass; 16 Vitest files / 32 tests pass; generated client and production Vite build pass. No strategy threshold, recommendation precedence, quantity calculation, or drawdown definition changed in UI-0.

## User-Friendly Strategy Manual — UI-1 — 2026-08-13

- The owner dashboard now follows one decision path: today's deterministic conclusion, no more than three server-ranked actions, portfolio safety, readiness, top risks, and a four-column holdings summary. It still uses the single `GET /api/v1/brief/today` aggregate and does not compose client-side recommendations.
- `TodayDecisionHero` distinguishes confirmed no-action, urgent action, incomplete data, stale analysis, and blocked/failed analysis. Incomplete or failed analysis never emits a calm conclusion or a precise quantity.
- Action cards now lead with the recommendation, server-supplied quantity when available, primary reason, and current-to-target position. Evidence, risk, confidence, validity, and change conditions remain available in a collapsed disclosure.
- Portfolio safety uses the brief's actual protected reserve, strategy drawdown, tactical/speculative exposure, and deployable cash. Market state and audit metadata are secondary disclosures rather than the first screen.
- Verification: frontend ESLint and TypeScript pass; 18 Vitest files / 35 tests pass; production Vite build passes. No recommendation, sizing, risk, or readiness value is hard-coded in the UI.

## User-Friendly Strategy Manual — UI-2 — 2026-08-13

- The holdings inventory is now a six-column desktop decision list and a responsive mobile card layout: holding, system recommendation, position, key reason, next event, and data status. Search supports symbol and company name; task-language filters cover action, event, readiness, and portfolio role.
- A four-item summary strip uses server brief truth for invested assets, deployable cash, protected living reserve, and today's must-act count. An unavailable brief renders unavailable values rather than client-side estimates.
- `PortfolioHoldingResponse.keyReason` exposes the first reason from the active deterministic recommendation, falling back to the latest holding analysis reason. The UI never infers a reason from price or weight and explicitly marks a missing reason as not yet formed.
- Classification confirmation remains scoped to the selected position and version. The mobile layout uses the same semantic table rows, so it does not duplicate controls or rely on forced clicks.
- Verification: the portfolio MySQL integration suite passes 11 tests, including persisted recommendation reason projection; the OpenAPI export and generated TypeScript client are current; frontend ESLint, workspace typecheck, 18 Vitest files / 36 tests, API-client build, and production Vite build pass.

## User-Friendly Strategy Manual — UI-3 — 2026-08-13

- Portfolio import is now a real five-step wizard—upload, inspect, confirm roles, protect living reserve, and final confirmation—with only the current task mounted. Mobile radio, checkbox, and button controls retain native click behavior and no test or implementation uses forced clicks.
- Upload defaults to Fidelity CSV and keeps paste/manual entry behind secondary tabs. Preview shows recognized counts and only error rows by default; successfully recognized holdings and cash are collapsed for optional review.
- Every non-ignored holding requires an explicit per-row role confirmation. Suggested roles and their reasons come from the import classification service; changing a role invalidates its confirmation until reviewed again.
- The preview contract now includes `emergencyCashTarget` from the active published strategy. Every cash location displays a confirmed amount; Fidelity-only setup shows imported cash, protected amount, remaining gap, and deployable remainder, while external balances are explicitly marked as manually supplied and unverifiable.
- Final confirmation summarizes real preview counts and cash amounts and reiterates that confirmation starts analysis but never logs into or submits orders to Fidelity.
- Verification: portfolio-import preview MySQL tests pass 2/2 and assert the server strategy target; OpenAPI and generated client are current; frontend ESLint, typecheck, 18 Vitest files / 36 tests, API-client build, and production Vite build pass. The wizard journey test uses ordinary `userEvent.click` for every radio, checkbox, and button.

## User-Friendly Strategy Manual — UI-4 — 2026-08-13

- The holding report now leads with the deterministic action, current/normal/hard position limits, reason, today's task, confidence, and actual data timestamp. When company fundamentals are unavailable it explicitly withholds a new trade conclusion and does not imply that the existing holding is safe.
- The investment-case sentence comes from the persisted deterministic decision narrative or recommendation rationale. Layers 1–2 are expanded by default and layers 3–6 are native collapsed disclosures, preserving the complete audit trail without making it the first screen.
- Owner-scoped position intelligence now exposes canonical TTM fundamentals, margins, net cash, dilution, 30/90-day estimate revisions, current valuation multiples, FCF yield, and five-year valuation percentile. Values are queried from financial, estimate, and valuation snapshots; missing observations remain null and render as “暂无可靠数据”, never zero-filled.
- The decision chart supports 3M, 6M, 1Y, and 3Y and retains only price, EMA20, SMA50, formal stop, live stop, and earnings markers. Range controls use normal buttons and native clicks. The hard limit continues to come exclusively from `layers.portfolioRole.hardMaxWeight`.
- Verification: the owner-scoped MySQL intelligence test passes with canonical fundamental/revision/valuation assertions; OpenAPI and generated TypeScript contracts are current; frontend ESLint, typecheck, 18 Vitest files / 38 tests, API-client build, and production Vite build pass. UI-4 adds explicit tests for disclosure defaults, missing-number honesty, and ordinary chart-range clicks.

## User-Friendly Strategy Manual — UI-5 — 2026-08-13

- Market & Opportunities now answers one question first: whether new money has a qualified use today. The status distinguishes incomplete analysis, a portfolio-wide risk pause, an observed ETF setup, a deployable ETF tranche, another engine-approved opportunity, and the normal no-special-entry state.
- Opportunity cards are projections of active deterministic recommendations only. They show the persisted reason, current-to-target position, server-estimated amount, confidence, risk calculation reason, risks, and change conditions; the browser does not screen securities or manufacture opportunities.
- ETF Dip exposes its canonical setup score, confirmation count/codes, portfolio and instrument drawdown, market-driven classification, Emergency Cash protection, tactical reserve before/after, and evidence quality. Detailed evidence is collapsed by default, and a non-ready setup explicitly says not to deploy the next tranche.
- The owner summary exposes the real deployable-cash, total planned-risk, cluster-risk, and data-completeness gates. Missing values remain unavailable, and an attractive asset absent from the engine-approved opportunities list is not presented as buyable.
- Verification: backend compilation and OpenAPI generation pass; frontend ESLint, typecheck, 18 Vitest files / 39 tests, API-client build, and production Vite build pass. The added opportunity test verifies the single top status and persisted ETF confirmation evidence.

## User-Friendly Strategy Manual — UI-6 — 2026-08-13

- Recommendation history now projects the persisted owner decision, rationale and acknowledgement time, plus the recommendation-time/latest canonical position weights and decision prices. Missing comparison points remain unavailable rather than being reconstructed from fabricated values.
- Review cards answer what the engine recommended, what the owner recorded, and what price/weight changed afterward. Every card explicitly states that subsequent price direction does not establish whether the original recommendation was correct; evaluation remains tied to contemporaneous evidence, exposure, and risk.
- Deferred or ignored MUST_ACT counts use acknowledgement records only. A recommendation the owner never opened is not silently classified as ignored.
- Active-sleeve performance remains explicitly unavailable until cash-flow-adjusted NAV, benchmark, and sleeve attribution are jointly present. The behavior reminder prevents concentration constraints from being reinterpreted as short-term price forecasts.
- Verification: backend compilation and generated contracts pass; frontend ESLint, typecheck, 18 Vitest files / 40 tests, API-client build, and production Vite build pass. The review test verifies real return/weight presentation and the no-right-or-wrong framing.

## User-Friendly Strategy Manual — UI-7 — 2026-08-13

- Settings now separates account/import, editable owner preferences, provider status, and read-only advanced strategy internals. Full Strategy YAML, ETF Dip weights, ATR multipliers, drawdown ladders, rule precedence, and risk formulas are not editable from the ordinary owner UI.
- Flyway V40 and owner-scoped preference APIs persist Emergency Cash target, manual broker, notification, starter-buy, primary ETF, and a tightly validated 0.1%–1% personal trade-risk cap. Updates use optimistic versions, row locking, CSRF, validation, and an audit event; stale writes return conflict.
- The settings form reads and writes only those bounded fields. It explicitly states that advanced deterministic parameters remain versioned server strategy, avoiding the impression that arbitrary browser inputs can override the engine.
- Verification: preference integration coverage proves version increments, stale-write rejection, and audit creation; Flyway applies 45 migrations through V40; OpenAPI/generated client, frontend ESLint/typecheck, 18 Vitest files / 40 tests, API-client build, and production Vite build pass.

## User-Friendly Strategy Manual — Strategy S-0 — 2026-08-13

- Portfolio confirmation now snapshots imported Fidelity account cash and open-position quantities before reconciliation. A first import establishes the NAV baseline without calling deposits investment returns.
- A later cash-only broker change automatically creates an idempotent `portfolio_external_cashflow_event` through `PortfolioNavService.recordExternalCashflow`; a simultaneous position and cash change creates `NAV_RECONCILIATION_REQUIRED` instead of guessing.
- The owner brief suppresses its drawdown number while NAV reconciliation is unresolved and explains that the system is distinguishing investment performance from external funding. No synthetic return or precise drawdown is shown.
- Verification: portfolio import integration tests pass 5/5; the dedicated MySQL reconciliation test proves baseline, defensible $7,000 cashflow recording, and rejection of an ambiguous $1,000 change; frontend 18 Vitest files / 40 tests and TypeScript pass.

## User-Friendly Strategy Manual — Strategy S-1 — 2026-08-13

- Drawdown attribution now uses peak quantity, post-peak executed quantity changes, execution cashflows, and current quantity/value. Sold positions remain eligible, and a post-peak add is no longer charged the full peak-to-current price decline.
- V41 adds explicit quantity delta, execution price, and realized P&L fields to the trade journal. A quantity change without complete execution evidence is excluded rather than assigned a fabricated precise loss.
- Position and cluster contributions use the monetary strategy NAV drawdown, `(high_water_nav - nav) × units`, as their common denominator. Persisted JSON now exposes positive `positionLossAmount` and contribution fractions.
- Verification: Flyway applies through V41; focused NAV and attribution tests pass. Tests prove a 50-share post-peak add, a 60-share post-peak sale, exclusion of incomplete execution evidence, loss ranking, and NAV-loss denominator alignment.

## User-Friendly Strategy Manual — Strategy S-2 — 2026-08-13

- V42 introduces position-scoped, immutable tactical catalyst evidence with explicit status, type, summary, as-of time, expected date, invalidation, and evidence checksum. Missing evidence is represented as `MISSING`, never inferred from price.
- Tactical ADD now requires a confirmed, complete, unexpired catalyst plus reversal confirmation, capacity below normal max, available portfolio and cluster risk, a formal stop, event evidence, and no behavioral firewall block.
- A price reversal with no qualified catalyst emits deterministic WATCH language: price has improved, but the owner should observe without adding. Missing cluster-risk evidence now fails closed instead of looking like zero risk.
- Verification: Flyway applies through V42; 25 decision-engine tests plus the MySQL migration test pass. Focused coverage proves the same tactical reversal produces ADD with complete catalyst evidence and WATCH when catalyst evidence is missing.

## User-Friendly Strategy Manual — Strategy S-3 — 2026-08-13

- Tactical stock, cyclical tactical, and turnaround tactical classifications now have an explicit readiness path instead of falling through the generic quality check.
- READY requires confirmed classification, a fresh completed price bar, trend, formal stop, confirmed catalyst, event evidence, fresh risk evidence, a live thesis, healthy capital evidence, and available portfolio/cluster capacity inputs.
- Missing catalyst, event, risk, or capacity evidence yields PARTIAL; missing/invalid thesis or formal stop yields BLOCKED; stale stop, catalyst, event, or risk evidence yields STALE. None of these states can create a precise tactical ADD.
- Verification: 28 focused readiness and decision tests pass, including READY, missing-catalyst PARTIAL, missing-stop BLOCKED, and stale-risk STALE branches.

## User-Friendly Strategy Manual — Strategy S-4 — 2026-08-13

- Stop recalculation now extracts the latest two separately confirmed swing lows. The earlier pivot is the structure input; the later pivot is passed as a higher-low input only when its price is strictly greater.
- The prior fallback that substituted an arbitrary 22-bar minimum was removed. One pivot, an equal/lower second pivot, or insufficient confirmation can no longer manufacture a higher-low stop raise.
- Verification: 8 strategy-core stop/intelligence tests and the new backend swing-structure test pass. Coverage proves distinct 8→9 pivots raise the structural input, while 8→7 and a lone 8 pivot pass no higher low.

## User-Friendly Strategy Manual — Strategy S-5 — 2026-08-13

- `DecisionAsOfContext` now carries market date, evidence cutoff, and strategy version. Analysis runs derive this context from their persisted run date/version; live analysis uses the injected clock.
- Point-in-time evidence access covers indicator, breadth, macro observation/factor, ETF Dip, market regime, estimate revision, and valuation assessment snapshots with both `market_date <= context.marketDate` and `data_as_of <= context.dataCutoff`; versioned strategy outputs also require the exact strategy version.
- Holding evidence assembly, regime inputs, breadth, macro, and ETF Dip reads now consume the context. Price bars, quotes, price state, fundamentals, estimates, and valuation snapshots are cutoff-bound, so historical runs do not silently read the latest database row.
- Verification: backend packaging passes; the focused MySQL replay test inserts a same-market-date indicator learned after the cutoff and a future indicator, then proves the store selects the older point-in-time value. Focused replay/readiness tests pass 4/4.

## User-Friendly Strategy Manual — Strategy S-6 — 2026-08-13

- Financial concepts now distinguish `DILUTED_WEIGHTED_AVG_SHARES` from point-in-time `COMMON_SHARES_OUTSTANDING`. V43 migrates the legacy diluted-share metric without reinterpreting it as outstanding shares.
- EPS and share-dilution calculations retain diluted weighted-average shares. Market capitalization now requires the most recent common shares outstanding, and that market cap flows through EV/Sales, Price/Sales, and FCF Yield.
- SEC and deterministic provider mappings include both concepts with explicit share units; missing common outstanding shares causes valuation computation to wait rather than fall back to the EPS denominator.
- Verification: 6 focused financial, SEC-provider, and valuation tests pass. A 98-share common basis at $20 proves market cap 1,960, P/S 1.96, EV/Sales 2.06, and the corresponding FCF yield independently of diluted weighted-average shares.

## User-Friendly Strategy Manual — Strategy S-7 — 2026-08-13

- Cash setup now treats `location` only as the declared location and requires an explicit `amount` for every choice. Selecting a location no longer fills the strategy target into the form.
- `EXTERNAL_BANK` persists exactly the amount the owner entered; it never assumes the $20,000 strategy target. Every positive external component is tagged `USER_CONFIRMED_EXTERNAL`. Pre-migration inferred records are honestly marked `LEGACY_INFERRED` rather than relabeled as owner-confirmed.
- An `IN_FIDELITY` confirmation cannot exceed cash observed in the imported broker file. Split and below-target confirmations allocate only the observed Fidelity portion internally and treat the remainder as explicit external confirmation. The UI shows the total, source split, target gap, and deployable Fidelity remainder without manufacturing precision.
- Verification: Flyway applies 49 migrations through V44; 4 focused MySQL confirmation/ownership tests pass, including a $7,000 external confirmation against a $20,000 target; OpenAPI generation, frontend ESLint/typecheck, and 18 Vitest files / 40 tests pass using ordinary user-event interaction.

## User-Friendly Strategy Manual — Final Release Gate — 2026-08-13

- The complete Maven reactor passes Spotless, Spring Modulith boundaries, and 219 backend tests. Replay context is an explicit named module interface, missing cluster/portfolio risk yields unavailable sizing rather than an exception, and legacy integration fixtures now preserve the fail-closed strategy semantics.
- The real-portfolio acceptance archetype no longer claims readiness when synthetic price history cannot prove confirmed swing structure. Its brief is correctly `BLOCKED`, `confirmedNoAction` remains false, and missing stops/cluster risk remain unavailable or zero rather than fabricated.
- Desktop Chromium and mobile Chromium run the same four Playwright journeys: actionable morning brief and acknowledgement, layered holding report, full five-step import, and no-false-calm under incomplete analysis. The import journey runs at 375px on mobile, uses normal clicks only, explicitly confirms each role, and submits an explicit cash amount.
- Release verification passes: frontend ESLint and TypeScript, 18 Vitest files / 40 tests, production build, OpenAPI drift check, dependency audit with no known vulnerabilities, Docker Compose validation, backend container image build, and 8/8 desktop/mobile E2E tests.

## Quality Entry Gates & Mobile Import Hardening — 2026-08-13

- Quality Deep Discount Starter now fails closed while price remains weak, in a downtrend, in breakdown, or missing. A starter requires at least a neutral/stabilizing state (`NEUTRAL` or `REVERSAL_SETUP`) or a confirmed upward state; subsequent starters also require the existing independent-confirmation evidence.
- Normal Quality ADD is now valuation-sensitive. `ATTRACTIVE` remains the primary path and accepts flat-or-better revisions with confirmed price action. `FAIR` requires improving revisions plus either `STRONG_UPTREND` or `REVERSAL_CONFIRMED`; `FAIR + FLAT + UPTREND` deterministically resolves to HOLD.
- The mobile Import Wizard no longer exposes unresolved rows as a horizontally scrolling desktop table. Error rows become labeled mobile cards, the progress stepper remains visible, and role confirmation presents exactly one holding at a time with ordinary previous/next controls.
- Desktop retains the efficient multi-holding review. The shared mobile E2E runs at 375×812, confirms both holdings through visible controls, checks horizontal overflow at the review and role stages, and completes the same explicit cash-confirmation journey without forced clicks.
- Focused strategy tests pass 30/30 and the complete Maven reactor passes 223 backend tests. Frontend ESLint, TypeScript, 18 Vitest files / 40 tests, production build, OpenAPI drift check, Compose validation, and desktop/mobile Playwright pass 8/8. The pnpm lock now overrides the newly disclosed vulnerable `nanoid <3.3.18` transitive range to 3.3.18, and the high-severity audit reports no known vulnerabilities.

## Fidelity CSV Auto-Recognition — 2026-08-16

- The parser now supports Fidelity's current Positions export semantics where the `Type` column contains settlement values such as `Cash` or `Margin` for ordinary securities. Known ETF/fund descriptions retain their explicit type; supported listed positions fall back to equity, while option, bond, Treasury, and certificate-of-deposit evidence remains unknown and fails closed.
- Fidelity export disclaimers, brokerage notices, and download-date metadata are ignored when they contain no symbol, description, quantity, or current value. They no longer become fake position rows that force manual correction.
- Fidelity core cash symbols including decorated `SPAXX**` normalize to canonical symbols and are recognized as cash. The preview now prominently displays the broker-observed cash total, and the cash step preselects `IN_FIDELITY` with that exact imported amount. It never substitutes the strategy target or fabricates external cash.
- Deterministically suggested holding roles are accepted into the preview automatically and remain editable. The owner still performs one final import confirmation, but no longer has to check every correctly recognized holding individually.
- A sanitized regression reproduces the August 2026 Fidelity header, settlement-type rows, decorated money-market symbol, trailing delimiter, and legal footer structure without committing the owner's source CSV or account data. The complete Maven reactor passes 224 backend tests; frontend ESLint, TypeScript, 18 Vitest files / 40 tests, and desktop/mobile Playwright pass 8/8.
