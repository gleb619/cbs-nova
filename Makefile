# cbs-nova — local development orchestration.
#
# Run `make help` to list every target (this is also the default goal).
# All targets delegate to scripts/cbs_cli.py, a single Python orchestrator
# that shares common logic across commands via reusable classes.

SHELL := /usr/bin/env bash
SCRIPT := scripts/cbs_cli.py

export COMPOSE := docker compose
export BACKEND_BUILDS := dsl-platform dsl-starter dsl-plugins
export GRADLEW := backend/dsl-platform/gradlew

.DEFAULT_GOAL := help

.PHONY: help
help: ## Show this help (default target)
	@printf 'cbs-nova — local development commands\n\n'
	@printf 'Usage:\n  make <target>\n\nTargets:\n'
	@awk 'BEGIN {FS = ":.*?## "} \
		/^[a-zA-Z_-]+:.*?## / {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

.PHONY: up
up: ## Start the docker compose stack and wait for Keycloak, Bugsink, Temporal
	@python3 $(SCRIPT) up

.PHONY: down
down: ## Stop the docker compose stack (keeps volumes)
	@python3 $(SCRIPT) down

.PHONY: logs
logs: ## Tail logs from all docker compose services
	@python3 $(SCRIPT) logs

.PHONY: clean
clean: ## Stop the stack AND delete all volumes (DESTRUCTIVE — wipes DB data)
	@python3 $(SCRIPT) clean

.PHONY: backend
backend: ## Run the Spring Boot starter
	@python3 $(SCRIPT) backend

.PHONY: frontend
frontend: ## Run the Nuxt admin UI dev server
	@python3 $(SCRIPT) frontend

.PHONY: publish
publish: ## Publish the DSL platform to Maven Local
	@python3 $(SCRIPT) publish

.PHONY: dev
dev: ## Bring up docker + run backend and frontend together
	@python3 $(SCRIPT) dev

.PHONY: typecheck
typecheck: ## Typecheck the frontend packages
	@cd frontend && pnpm typecheck

.PHONY: lint
lint: ## Run all lint/format checks (backend Spotless + frontend Biome); non-zero exit on any failure
	@python3 $(SCRIPT) lint all

.PHONY: lint-backend
lint-backend: ## Backend format check (spotlessCheck on dsl-platform, dsl-starter, dsl-plugins)
	@python3 $(SCRIPT) lint-backend

.PHONY: lint-frontend
lint-frontend: ## Frontend lint (Biome)
	@python3 $(SCRIPT) lint-frontend

.PHONY: fmt
fmt: ## Apply formatting everywhere (spotlessApply + pnpm format); fixes files, always exits 0
	@python3 $(SCRIPT) fmt

.PHONY: doctor
doctor: ## Run smoke checks against the running stack and report per-check health
	@python3 $(SCRIPT) doctor

.PHONY: seed
seed: ## Seed a hello-world DSL definition + one sample run (idempotent)
	@python3 $(SCRIPT) seed

.PHONY: seed-history
seed-history: ## Seed up to 5 historical sample runs (seed-history-*, mixed statuses, idempotent)
	@python3 $(SCRIPT) seed-history

.PHONY: loadtest
loadtest: ## Load-test read-only BFF endpoints and report latency percentiles + error rate
	@python3 $(SCRIPT) loadtest
