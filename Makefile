.PHONY: bootstrap infra-up infra-down db-migrate backend-test web-test contract-generate dev verify

bootstrap:
	./mvnw -B -DskipTests install
	pnpm install --frozen-lockfile

infra-up:
	docker compose -f infra/compose.local.yaml up -d mysql

infra-down:
	docker compose -f infra/compose.local.yaml down

db-migrate:
	docker compose -f infra/compose.local.yaml --profile ops run --rm migration

backend-test:
	./mvnw verify

web-test:
	pnpm lint
	pnpm typecheck
	pnpm test
	pnpm build

contract-generate:
	pnpm api:generate

dev:
	docker compose -f infra/compose.local.yaml --profile app up --build -d
	pnpm dev

verify:
	./mvnw spotless:check
	./mvnw verify
	pnpm lint
	pnpm typecheck
	pnpm test
	pnpm build
	pnpm api:check
	pnpm --filter @portfolio/web test:e2e
	docker compose -f infra/compose.local.yaml config --quiet
