# PROJECT_STATE

## Current Phase

Hardening Gate 0 completed on 2026-08-10 in commit `449977b`: the reproducible merge gates now include current pinned GitHub Actions, executable Maven wrapper metadata, Spotless UNIX line endings, dependency review, Gitleaks, pnpm audit, Docker build, frontend type/lint/unit/build/API/E2E gates, and a documented branch-protection contract. Local verification passed; remote GitHub branch-protection enforcement remains explicitly unverified because no authenticated GitHub session or CLI credential is available in this environment.

Hardening A1 implements canonical mark-to-market as an append-only `position_mark_snapshot` plus deterministic latest-mark view. Completed adjusted daily closes now drive capital, weights, cluster contribution, drawdown/equity, holding evidence, earnings weights, portfolio APIs, and executive brief metrics; imported broker `market_value` remains provenance evidence only. Missing/stale marks propagate non-healthy capital quality and block exact sizing or drawdown rather than silently falling back. The analysis pipeline captures marks before portfolio-dependent computation. Regression coverage proves price revaluation without re-import, preservation of broker evidence, missing-mark fail-closed behavior, current-weight movement across a hard-cap boundary, and marked portfolio equity.

Hardening A2 replaces global magic-rank conflict selection with explicit `DecisionChannel` and `EvidenceDependency` semantics. Independently established EXIT, REDUCE_HALF, and TRIM candidates resolve in a risk-reduction channel before new-risk eligibility or maintenance candidates; EXIT is strongest, followed by REDUCE_HALF and TRIM. Emergency reserve, pain-line, DO_NOT_ADD, or WAIT_FOR_DATA candidates can block new exposure but cannot suppress an already validated reduction. The seven-case precedence matrix and Quality/Tactical/Speculative integration cases pass.

Hardening A3 makes broad-US and technology core targets aggregate sleeve targets instead of per-instrument targets. `PortfolioAllocationService` computes and snapshots BROAD_CORE, TECH_CORE, QUALITY, THEMATIC, TACTICAL, SPECULATIVE, and CASH_RESERVE allocations from canonical marks and investable capital. Ordinary Core ETF gap filling is restricted to the configured primary instrument (`VOO` or `QQQM`); alternate ETFs contribute to the sleeve but do not independently fill its gap. Tests prove QQQM 8% + VGT 7% and VOO 20% + SPY 15% produce no buy, while a 10% technology sleeve exposes only the primary QQQM to the 5% aggregate gap.

Hardening A4 replaces the placeholder ETF-dip row count with `EtfDipEventService`, which assembles canonical portfolio/benchmark/indicator/reserve evidence, evaluates the shared `EtfDipEngine` using Strategy V2 thresholds and tranches, and appends auditable event snapshots with trigger codes, tranche/cooldown state, reserve projection, quality, strategy hash, rules, and checksum. UPDATE_DIP_EVENTS now runs before holding analysis; the formal Core ETF engine can deploy only from a non-expired `READY_FOR_TRANCHE_n` event and never reimplements drawdown qualification. Dip sizing uses tactical reserve × configured tranche percentage, capped by deployable cash and remaining aggregate sleeve capacity. A drawdown state without a canonical event cannot deploy, and the full real-portfolio pipeline reaches recommendation generation with the new ordering.

Hardening A5 establishes append-only `risk_cluster_snapshot` as the canonical cluster-risk source. `ClusterRiskService` sums each cluster member's latest open-risk amount and divides once by investable assets, preserving member count, evidence quality, strategy version/hash, data-as-of, and checksum. The deprecated per-position `cluster_risk_fraction` is zeroed and no longer consumed. Holding evidence, portfolio constraints, position sizing, Position Report, and Executive Brief now converge on the canonical snapshot; integration coverage proves a 240 + 200 risk cluster over 80,000 investable assets is 440 / 0.55%, and a canonical 1.00% cluster blocks new risk above the 0.75% cap.

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
