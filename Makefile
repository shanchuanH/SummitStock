.PHONY: bootstrap infra-up infra-down db-migrate backend-test web-test contract-generate dev verify

bootstrap:
	./mvnw -B -DskipTests install
	pnpm install --frozen-lockfile

infra-up:
	docker compose -f infra/compose.yaml up -d mysql

infra-down:
	docker compose -f infra/compose.yaml down

db-migrate:
	docker compose -f infra/compose.yaml --profile ops run --rm migration

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
	@echo "Run './mvnw -pl apps/backend -am spring-boot:run' and 'pnpm dev' in separate terminals."

verify:
	./mvnw spotless:check
	./mvnw verify
	pnpm lint
	pnpm typecheck
	pnpm test
	pnpm build
	pnpm api:check
	pnpm --filter @portfolio/web test:e2e
	docker compose -f infra/compose.yaml config --quiet
