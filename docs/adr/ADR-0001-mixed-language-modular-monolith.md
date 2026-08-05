# ADR-0001: Mixed-language modular monolith

- Status: Accepted
- Date: 2026-08-05
- Owners: Portfolio Engine

## Context

The product needs exact, testable portfolio rules, a durable operational runtime, and a browser UI without duplicating financial logic. Packet 01 also requires independently testable API and worker processes while avoiding premature distributed-system complexity.

## Decision

Use a Maven reactor with pure-Java `quant-core` and `strategy-core` libraries and one Spring Modulith backend artifact. The artifact starts in `api` or `worker` mode. MySQL is the durable source of truth. The React/TypeScript SPA handles presentation and interaction only. Spring-generated OpenAPI is the sole API contract and generates the frontend client.

## Alternatives

- TypeScript-only full stack: rejected because it weakens the requested pure-Java quant/strategy boundary.
- Independent API and worker repositories: rejected because it duplicates configuration and domain code at this stage.
- Microservices with a queue or Redis: rejected because Packet 01 has no scale or isolation requirement that justifies operational complexity.

## Consequences

### Positive

- Financial invariants can be tested without Spring or a database.
- Modulith verification detects accidental package coupling.
- API and worker share migrations, domain types, and configuration.
- Frontend contract drift is visible as a checked-in generated diff.

### Negative

- Java and TypeScript toolchains must both remain healthy.
- Runtime-mode conditionals require explicit smoke testing.
- MySQL remains required for meaningful backend integration tests.

## Migration / Rollback

Modules can later be extracted behind their existing package boundaries. Rollback is a single-repository revert plus Flyway-compatible forward migration; an applied migration is never edited in place.

## Validation

- `ApplicationModules.of(PortfolioApplication.class).verify()`
- Maven reactor compile and integration tests on MySQL 8.4
- OpenAPI export followed by TypeScript generation and build
- API/worker Compose configuration validation

## Related Rule IDs / Strategy Versions

- `CASH.EMERGENCY.001`
- `COMPENSATION.UNVESTED.001`
- `1.0.0-draft`
