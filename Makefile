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

.PHONY: doctor
doctor: ## Run smoke checks against the running stack and report per-check health
	@printf '\n==> Running smoke checks...\n\n'; \
	fails=0; \
	\
	if [ -n "$$($(COMPOSE) ps -q 2>/dev/null)" ]; then \
		printf '    [ok]   Docker compose stack has running services\n'; \
	else \
		printf '    [fail] Docker compose stack has running services\n'; \
		fails=$$((fails+1)); \
	fi; \
	\
	if curl --silent --show-error --fail --max-time 5 http://localhost:8080/realms/master >/dev/null 2>&1; then \
		printf '    [ok]   Keycloak (http://localhost:8080/realms/master)\n'; \
	else \
		printf '    [fail] Keycloak (http://localhost:8080/realms/master)\n'; \
		fails=$$((fails+1)); \
	fi; \
	\
	if curl --silent --show-error --fail --max-time 5 http://localhost:8000/ >/dev/null 2>&1; then \
		printf '    [ok]   Bugsink (http://localhost:8000/)\n'; \
	else \
		printf '    [fail] Bugsink (http://localhost:8000/)\n'; \
		fails=$$((fails+1)); \
	fi; \
	\
	BE=$${BACKEND_BASE_URL:-http://localhost:$${SERVER_PORT:-8090}}; \
	body=$$(curl --silent --show-error --fail --max-time 5 "$$BE/actuator/health" 2>/dev/null) || body=""; \
	if echo "$$body" | grep -q '"status":"UP"'; then \
		printf '    [ok]   Backend actuator health (%s)\n' "$$BE/actuator/health"; \
	else \
		printf '    [fail] Backend actuator health (%s)\n' "$$BE/actuator/health"; \
		fails=$$((fails+1)); \
	fi; \
	\
	if $(COMPOSE) config --services 2>/dev/null | grep -qx 'temporal'; then \
		if curl --silent --show-error --fail --max-time 5 http://localhost:8233/ >/dev/null 2>&1; then \
			printf '    [ok]   Temporal UI (http://localhost:8233/)\n'; \
		else \
			printf '    [fail] Temporal UI (http://localhost:8233/)\n'; \
			fails=$$((fails+1)); \
		fi; \
	else \
		printf '    [skip] Temporal service not declared in compose — skipping Temporal health check\n'; \
	fi; \
	\
	BFF=$${BFF_BASE_URL:-http://localhost:3000}; \
	if curl --silent --show-error --fail --max-time 5 "$$BFF" >/dev/null 2>&1; then \
		printf '    [ok]   BFF reachable (%s)\n' "$$BFF"; \
	else \
		printf '    [fail] BFF reachable (%s)\n' "$$BFF"; \
		fails=$$((fails+1)); \
	fi; \
	\
	helpers_body=$$(curl --silent --show-error --fail --max-time 5 "$$BFF/api/v1/dsl/helpers" 2>/dev/null) || helpers_body=""; \
	if [ -n "$$helpers_body" ] && echo "$$helpers_body" | grep -q '\['; then \
		if command -v jq >/dev/null 2>&1; then \
			if echo "$$helpers_body" | jq -e 'length > 0' >/dev/null 2>&1; then \
				printf '    [ok]   DSL helpers catalog non-empty (%s)\n' "$$BFF/api/v1/dsl/helpers"; \
			else \
				printf '    [fail] DSL helpers catalog empty (%s)\n' "$$BFF/api/v1/dsl/helpers"; \
				fails=$$((fails+1)); \
			fi; \
		else \
			printf '    [ok]   DSL helpers catalog non-empty (%s)\n' "$$BFF/api/v1/dsl/helpers"; \
		fi; \
	else \
		printf '    [fail] DSL helpers catalog unreachable or invalid (%s)\n' "$$BFF/api/v1/dsl/helpers"; \
		fails=$$((fails+1)); \
	fi; \
	\
	if curl --silent --show-error --max-time 5 -o /dev/null -w '%{http_code}' "$$BFF/api/v1/dsl/definitions" 2>/dev/null | grep -q '^200$$'; then \
		printf '    [ok]   DSL definitions list (%s)\n' "$$BFF/api/v1/dsl/definitions"; \
	else \
		printf '    [fail] DSL definitions list (%s)\n' "$$BFF/api/v1/dsl/definitions"; \
		fails=$$((fails+1)); \
	fi; \
	\
	if [ $$fails -gt 0 ]; then \
		printf '\n%d check(s) failed.\n' $$fails; \
		exit 1; \
	else \
		printf '\nAll required checks passed.\n'; \
	fi
.PHONY: seed
seed: ## Seed a hello-world DSL definition + one sample run (idempotent)
	@printf '\n==> Seeding sample DSL definition (seed-hello-world)...\n'; \
	BFF=$${BFF_BASE_URL:-http://localhost:3000}; \
	if ! curl --silent --fail --max-time 5 "$$BFF" >/dev/null 2>&1; then \
		printf '    [fail] BFF not reachable at %s — run: make up && make backend && make frontend (see: make doctor)\n' "$$BFF"; \
		exit 1; \
	fi; \
	defs=""; \
	defs=$$(curl --silent --max-time 5 "$$BFF/api/v1/dsl/definitions" 2>/dev/null) || defs=""; \
	if [ -n "$$defs" ]; then \
		if command -v jq >/dev/null 2>&1; then \
			if printf '%s' "$$defs" | jq -e '.. | select(.name? == "seed-hello-world")' >/dev/null 2>&1; then \
				printf '    [skip] seed-hello-world already present\n'; \
				exit 0; \
			fi; \
		else \
			if printf '%s' "$$defs" | grep -q '"name":"seed-hello-world"'; then \
				printf '    [skip] seed-hello-world already present\n'; \
				exit 0; \
			fi; \
		fi; \
	fi; \
	draft_body='{"name":"seed-hello-world","type":"Process","status":"Draft","version":"1","taskQueue":"default","description":"seeded by make seed (uuidV7 + formatMessage catalog helpers)"}'; \
	if curl --silent --show-error --fail --max-time 10 -X POST "$$BFF/api/v1/dsl/drafts/seed-hello-world/save" \
			-H 'Content-Type: application/json' -d "$$draft_body" >/dev/null 2>&1; then \
		printf '    [ok]   saved draft seed-hello-world\n'; \
	else \
		printf '    [fail] save draft seed-hello-world\n'; \
		exit 1; \
	fi; \
	pub_body='{"name":"seed-hello-world","type":"Process","status":"Published","version":"1","taskQueue":"default","description":"seeded by make seed (uuidV7 + formatMessage catalog helpers)"}'; \
	if curl --silent --show-error --fail --max-time 15 -X POST "$$BFF/api/v1/dsl/drafts/seed-hello-world/publish" \
			-H 'Content-Type: application/json' -d "$$pub_body" >/dev/null 2>&1; then \
		printf '    [ok]   published seed-hello-world\n'; \
	else \
		printf '    [fail] publish seed-hello-world\n'; \
		exit 1; \
	fi; \
	printf '    [ok]   runnable definition seed-hello-world ready (uses uuidV7 + formatMessage helpers)\n'; \
	if curl --silent --show-error --fail --max-time 30 -X POST "$$BFF/api/v1/dsl/run/seed-hello-world" \
			-H 'Content-Type: application/json' -d '{"body":{},"metadata":{"source":"make seed"}}' >/dev/null 2>&1; then \
		printf '    [ok]   triggered sample run for seed-hello-world\n'; \
	else \
		printf '    [warn] could not trigger sample run for seed-hello-world (run it from the dashboard)\n'; \
	fi

.PHONY: seed-history
seed-history: ## Seed up to 5 historical sample runs (seed-history-*, mixed statuses, idempotent)
	@printf '\n==> Seeding historical sample runs (seed-history-*, cap 5)...\n'; \
	BFF=$${BFF_BASE_URL:-http://localhost:3000}; \
	if ! curl --silent --fail --max-time 5 "$$BFF" >/dev/null 2>&1; then \
		printf '    [fail] BFF not reachable at %s — run: make up && make backend && make frontend (see: make doctor)\n' "$$BFF"; \
		exit 1; \
	fi; \
	current=0; \
	txs=""; \
	txs=$$(curl --silent --max-time 5 "$$BFF/api/v1/dsl/transactions" 2>/dev/null) || txs=""; \
	if [ -n "$$txs" ]; then \
		if command -v jq >/dev/null 2>&1; then \
			current=$$(printf '%s' "$$txs" | jq -e '[.. | select((.name? // "") | startswith("seed-history-"))] | length' 2>/dev/null || printf '0'); \
		else \
			current=$$(printf '%s' "$$txs" | grep -o 'seed-history-' | wc -l); \
		fi; \
	fi; \
	current=$${current:-0}; \
	if [ "$$current" -ge 5 ] 2>/dev/null; then \
		printf '    [skip] already at cap (5) of seed-history-* runs\n'; \
		exit 0; \
	fi; \
	remaining=$$((5 - $$current)); \
	i=0; \
	while [ "$$i" -lt "$$remaining" ]; do \
		n=$$((5 - $$remaining + $$i + 1)); \
		if curl --silent --show-error --max-time 30 -X POST "$$BFF/api/v1/dsl/run/seed-hello-world" \
				-H 'Content-Type: application/json' \
				-d "{\"body\":{},\"metadata\":{\"tag\":\"seed-history-$$n\"}}" >/dev/null 2>&1; then \
			printf '    [ok]   seed-history-%d triggered\n' "$$n"; \
		else \
			printf '    [warn] seed-history-%d could not be triggered (best-effort, mixed statuses not guaranteed)\n' "$$n"; \
		fi; \
		i=$$((i+1)); \
	done; \
	printf '    Seeding history complete (up to %d runs).\n' "$$remaining"
