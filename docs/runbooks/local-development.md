# Local development runbook

## Environment

Use JDK 25 and the checked-in Maven Wrapper. Node.js must be 22.12 or newer and pnpm must be 11.x. Docker provides MySQL 8.4 for local runtime and Testcontainers.

1. Copy `.env.example` to `.env` and change credentials if the machine is shared.
2. Run `make dev` (or `docker compose -f infra/compose.local.yaml --profile app up --build -d` followed by `pnpm dev` on Windows without Make).
3. Open `http://127.0.0.1:4173`. The API and Worker are separate containers; do not omit the Worker.

MySQL is configured for InnoDB, `utf8mb4`, UTC, and strict SQL mode. Flyway owns schema initialization; Hibernate is validation-only. Do not enable Hibernate schema creation.

## Runtime modes

- `PORTFOLIO_RUNTIME_MODE=api`: servlet API, Actuator, security, and OpenAPI.
- `PORTFOLIO_RUNTIME_MODE=worker`: non-web process using the same domain and persistence code.

`local-fixture` is a deterministic product demo. It enables the draft strategy only in that profile, supplies fixture market/fundamental/estimate/earnings/macro evidence, and displays `DEMO / FIXTURE DATA` in the UI.

`local-live` uses configured real providers. A missing provider credential remains explicitly partial; it never falls back to fixture data. Run API and Worker with `SPRING_PROFILES_ACTIVE=local-live` and the required provider environment variables.

Use `docker compose -f infra/compose.local.yaml --profile app up --build` to run MySQL plus both modes in containers.

## Contract workflow

Run `pnpm api:generate` after a controller or DTO change. Review both `contracts/openapi/portfolio-api.json` and `contracts/generated/src/schema.d.ts`. CI runs `pnpm api:check` and rejects uncommitted contract drift.

## Troubleshooting

- A missing foundation table means Flyway auto-configuration or database credentials are wrong; do not switch `ddl-auto` away from `validate`.
- A `SYSTEM` MySQL session time zone means the JDBC URL lost `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`.
- A missing `SESSION` cookie means Spring Session JDBC is not active or its Flyway tables are unavailable.
- `GET /api/v1/analysis/runtime` reports Worker heartbeat and durable queue counts. A heartbeat older than 60 seconds is offline.
- A saved import with `WORKER_OFFLINE` is not lost; start `backend-worker`, then use **重新分析** or let the queued job be consumed.
