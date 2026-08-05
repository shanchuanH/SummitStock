# Local development runbook

## Environment

Use JDK 25 and the checked-in Maven Wrapper. Node.js must be 22.12 or newer and pnpm must be 11.x. Docker provides MySQL 8.4 for local runtime and Testcontainers.

1. Copy `.env.example` to `.env` and change credentials if the machine is shared.
2. Start MySQL with `docker compose -f infra/compose.yaml up -d mysql`.
3. Start the API with `.\mvnw.cmd -pl apps/backend -am spring-boot:run`.
4. Start the SPA with `pnpm dev`.

MySQL is configured for InnoDB, `utf8mb4`, UTC, and strict SQL mode. Flyway owns schema initialization; Hibernate is validation-only. Do not enable Hibernate schema creation.

## Runtime modes

- `PORTFOLIO_RUNTIME_MODE=api`: servlet API, Actuator, security, and OpenAPI.
- `PORTFOLIO_RUNTIME_MODE=worker`: non-web process using the same domain and persistence code.

Use `docker compose -f infra/compose.yaml --profile app up --build` to run MySQL plus both modes in containers.

## Contract workflow

Run `pnpm api:generate` after a controller or DTO change. Review both `contracts/openapi/portfolio-api.json` and `contracts/generated/src/schema.d.ts`. CI runs `pnpm api:check` and rejects uncommitted contract drift.

## Troubleshooting

- A missing foundation table means Flyway auto-configuration or database credentials are wrong; do not switch `ddl-auto` away from `validate`.
- A `SYSTEM` MySQL session time zone means the JDBC URL lost `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`.
- A missing `SESSION` cookie means Spring Session JDBC is not active or its Flyway tables are unavailable.
