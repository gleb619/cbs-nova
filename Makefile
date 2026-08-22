# cbs-nova — local development orchestration.
#
# Run `make help` to list every target (this is also the default goal).

SHELL := /usr/bin/env bash
COMPOSE := docker compose

.DEFAULT_GOAL := help

.PHONY: help
help: ## Show this help (default target)
	@printf 'cbs-nova — local development commands\n\n'
	@printf 'Usage:\n  make <target>\n\nTargets:\n'
	@awk 'BEGIN {FS = ":.*?## "} \
		/^[a-zA-Z_-]+:.*?## / {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

.PHONY: up
up: ## Start the docker compose stack and wait for Keycloak, Bugsink, Temporal
	$(COMPOSE) up -d
	@printf '\n==> Waiting for Keycloak (http://localhost:8080/realms/master)...\n'
	@curl --silent --show-error --fail --retry 60 --retry-delay 2 --retry-connrefused --max-time 5 \
		http://localhost:8080/realms/master >/dev/null \
		&& printf '    [ok] Keycloak is reachable.\n' \
		|| { printf '    [fail] Keycloak did not become ready in time. Inspect with: docker compose logs keycloak\n'; exit 1; }
	@printf '==> Waiting for Bugsink (http://localhost:8000)...\n'
	@curl --silent --show-error --fail --retry 60 --retry-delay 2 --retry-connrefused --max-time 5 \
		http://localhost:8000/ >/dev/null \
		&& printf '    [ok] Bugsink is reachable.\n' \
		|| { printf '    [fail] Bugsink did not become ready in time. Inspect with: docker compose logs bugsink\n'; exit 1; }
	@if $(COMPOSE) config --services 2>/dev/null | grep -qx 'temporal'; then \
		printf '==> Waiting for Temporal UI (http://localhost:8233)...\n'; \
		curl --silent --show-error --fail --retry 60 --retry-delay 2 --retry-connrefused --max-time 5 \
			http://localhost:8233/ >/dev/null \
			&& printf '    [ok] Temporal is reachable.\n' \
			|| { printf '    [fail] Temporal did not become ready in time. Inspect with: docker compose logs temporal\n'; exit 1; }; \
	else \
		printf '    [skip] Temporal service not declared in compose — skipping Temporal health check.\n'; \
	fi
	@printf '\nAll services are ready. Useful URLs:\n'
	@printf '  Spring Boot   http://localhost:8090  (started by `make backend`)\n'
	@printf '  Nuxt admin UI http://localhost:3000  (started by `make frontend`)\n'
	@printf '  Keycloak      http://localhost:8080  (admin / admin)\n'
	@printf '  Bugsink       http://localhost:8000  (admin / admin)\n'
	@printf '  Temporal UI   http://localhost:8233\n'
	@printf '  Temporal gRPC localhost:7233\n'

.PHONY: down
down: ## Stop the docker compose stack (keeps volumes)
	$(COMPOSE) down

.PHONY: backend
backend: ## Run the Spring Boot starter (backend/dsl-platform/gradlew -p backend/dsl-starter :starter-launcher:bootRun)
	SERVER_PORT=$${SERVER_PORT:-8090} backend/dsl-platform/gradlew -p backend/dsl-starter :starter-launcher:bootRun

.PHONY: publish
publish: ## Publish the DSL platform to Maven Local (backend/dsl-platform/gradlew -p backend/dsl-platform publishToMavenLocal -x test)
	backend/dsl-platform/gradlew -p backend/dsl-platform publishToMavenLocal -x test

.PHONY: frontend
frontend: ## Run the Nuxt admin UI dev server (cd frontend && pnpm dev)
	cd frontend && pnpm dev

.PHONY: dev
dev: ## Bring up docker + run backend and frontend together (delegates to scripts/dev.sh)
	bash scripts/dev.sh

.PHONY: logs
logs: ## Tail logs from all docker compose services
	$(COMPOSE) logs -f

.PHONY: clean
clean: ## Stop the stack AND delete all volumes (DESTRUCTIVE — wipes DB data)
	$(COMPOSE) down -v