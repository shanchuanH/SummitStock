# ADR-0006: Confirmed ETF dip tranches and emergency-first cashflow

- Status: Accepted
- Date: 2026-08-05

## Decision

ETF dip recommendations require complete data, a market-driven portfolio drawdown of at least 15%, emergency cash protection, setup score at least 60, and two independent reversal triggers. A 20% drawdown blocks new risk. Reserve deployment is 20/25/30/25, each tranche is unique, and later tranches require five trading days. Recovery may reduce only the Tactical Overlay. Every result is manual-only.

Monthly allocation first fills the $20,000 emergency floor. With the floor satisfied, the $7,000 sustainable surplus allocates $3,500 Broad Core, $1,050 Tech Core, $700 International, $1,050 Tactical Reserve, and $700 Quality Opportunity. Without a Quality signal, that $700 falls back to Broad Core. Active-sleeve budgets fall to 75% after failed 12-month accountability and 50% after 24 months when risk-adjusted improvement is absent.

## Consequences

- A price decline alone never activates ETF buying.
- Database uniqueness prevents duplicate tranche recommendations and idempotency-key reuse.
- Recommendation history remains advisory and never submits execution.
- Empty events render `NO ACTION`, which is the normal state.

## Migration and validation

Flyway `V6__etf_dip_cashflow_and_accountability.sql` adds dip event/tranche, cashflow allocation, and active-sleeve review snapshots. Pure-Java tests cover market attribution, triggers, cooldown, fractions, emergency cash, fallback, and 12/24-month accountability. MySQL/API/UI tests cover V6 and manual-only responses.
