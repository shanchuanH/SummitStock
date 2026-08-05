# ADR-0005: EOD stops and user-owned thesis state

- Status: Accepted
- Date: 2026-08-05
- Owners: Portfolio Engine

## Context

Packet 05 needs actionable stop levels without intraday drift, structured investment theses that an LLM cannot silently alter, valuation restraint, earnings-event policy, and risk-first trade journaling.

## Decision

Calculate individual-stock stops in pure Java after completed daily-bar evidence. The initial stop is the lower of structure-minus-0.25 ATR and the classification ATR stop. The live stop is monotonic and uses the maximum of the prior stop, Chandelier, EMA20-minus-0.5 ATR, and confirmed-higher-low-minus-0.25 ATR. Soft, close-confirmed, and catastrophic levels are explicit. Core ETFs do not use ordinary individual-stock stops.

Thesis status is structured, source-backed, expiring, explicitly confirmed by the user, and protected by optimistic concurrency. LLMs may summarize evidence but cannot write thesis status. Quality Discount requires fundamental health, improving revisions, stabilization, and capacity; its 1%/1% tactical lots remain separate and capped at 3%. Earnings policy uses 8–12 historical events and differs by holding class. Journal metrics use decimal planned/realized R, MFE, MAE, tax state, and exit reason while risk decisions retain priority over tax optimization.

## Consequences

- Formal stops are reproducible, non-decreasing, and deduplicated per alert type/market date.
- Cheap price alone cannot trigger a Quality purchase.
- Thesis confirmation and overrides are auditable.
- Position Detail can show honest empty/stale states until workers produce evidence.
- Intraday formal stop recalculation and automatic execution remain prohibited.

## Migration / Rollback

Flyway `V5__stops_thesis_valuation_earnings_and_journal.sql` adds thesis/source, stop snapshot/alert, valuation, earnings-risk, and trade-journal tables. Applied migrations are immutable.

## Validation

- Stop monotonicity, Core ETF exemption, close/catastrophic levels, and decimal R/MFE/MAE tests
- Quality cheap-with-falling-revisions and stabilized-discount golden tests
- Speculative, tactical, and Quality earnings-policy tests
- MySQL V5, user isolation, duplicate alert, thesis concurrency/audit, and decimal-string API tests
- Position Detail ready, empty, stale, and authentication-error UI tests

## Rule IDs

- `STOP.CORE_ETF.001`, `STOP.INITIAL.001`, `STOP.MONOTONIC.001`
- `STOP.SOFT_ALERT.001`, `STOP.CLOSE_CONFIRMED.001`, `STOP.CATASTROPHIC.001`
- `THESIS.CONFIRM.001`, `THESIS.EXPIRY.001`
- `VALUATION.REVISIONS.001`, `VALUATION.STABILIZATION.001`, `VALUATION.TACTICAL_CAP.001`
- `EARNINGS.QUALITY.001`, `EARNINGS.TACTICAL.001`, `EARNINGS.SPECULATIVE.001`
- `JOURNAL.RISK_FIRST.001`
