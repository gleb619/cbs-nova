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

# Minimum number of paths expected in /v3/api-docs. Bump when new DSL routes are added.
MIN_OPENAPI_PATHS := 37

.PHONY: openapi
openapi: ## Regenerate docs/openapi.json from the live /v3/api-docs (needs Postgres; Temporal optional)
	@set -euo pipefail; \
	PORT=$${SERVER_PORT:-8090}; BASE=http://localhost:$$PORT; \
	printf '\n==> Booting starter-launcher headless on port %s...\n' "$$PORT"; \
	LOG=$$(mktemp /tmp/cbs-nova-openapi-boot.XXXXXX.log); \
	cleanup() { pkill -f 'cbs.nova.starter.[S]tarterApplication' 2>/dev/null || true; rm -f "$$LOG"; }; \
	trap cleanup EXIT; \
	env SERVER_PORT=$$PORT \
		backend/dsl-platform/gradlew -p backend/dsl-starter :starter-launcher:bootRun --console=plain >"$$LOG" 2>&1 & \
	up=0; for i in $$(seq 1 40); do \
		if curl -sf --max-time 2 "$$BASE/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then up=1; break; fi; \
		sleep 3; done; \
	if [ $$up -ne 1 ]; then \
		printf '    [fail] backend not healthy after ~120s (log: %s)\n' "$$LOG" >&2; \
		tail -20 "$$LOG" >&2; \
		exit 1; \
	fi; \
	printf '    [ok]   backend healthy (%s/actuator/health)\n' "$$BASE"; \
	TMP=$$(mktemp /tmp/cbs-nova-openapi.XXXXXX.json); \
	curl -sf --max-time 30 "$$BASE/v3/api-docs" | python3 -c \
		'import json,sys; print(json.dumps(json.load(sys.stdin), indent=2, sort_keys=True))' > "$$TMP"; \
	python3 -c \
		'import json,sys; d=json.load(open(sys.argv[1])); n=len(d.get("paths",{})); assert n>=int(sys.argv[2]), f"paths count {n} < {sys.argv[2]}"; print(f"    [ok]   {n} paths (>= {sys.argv[2]})")' \
		"$$TMP" $(MIN_OPENAPI_PATHS); \
	mv "$$TMP" docs/openapi.json; \
	printf '    [ok]   wrote docs/openapi.json\n'; \
	printf '==> Shutting down backend...\n'; \
	pkill -f 'cbs.nova.starter.[S]tarterApplication' 2>/dev/null || true; \
	for i in $$(seq 1 15); do \
		curl -s --max-time 1 "$$BASE/" >/dev/null 2>&1 || break; sleep 1; done; \
	if curl -s --max-time 1 "$$BASE/" >/dev/null 2>&1; then \
		printf '    [fail] backend still serving on %s\n' "$$BASE" >&2; exit 1; \
	fi; \
	trap - EXIT; rm -f "$$LOG"; \
	printf '    [ok]   backend stopped, docs/openapi.json regenerated\n'

# NOT wired into `make test` — needs a Postgres-backed bootRun. Opt-in mirror
# of `make cve-scan` (T414). Detects drift between the committed docs/openapi.json
# and a freshly fetched spec without ever writing docs/openapi.json.
.PHONY: openapi-check
openapi-check: ## Fail if docs/openapi.json drifts from live /v3/api-docs (classifier: scripts/openapi-diff.py)
	@set -euo pipefail; \
	TMP=$$(mktemp /tmp/cbs-nova-openapi-check.XXXXXX.json); \
	cleanup() { rm -f "$$TMP"; }; \
	trap cleanup EXIT; \
	MIN_OPENAPI_PATHS=$(MIN_OPENAPI_PATHS) bash scripts/openapi-fetch.sh "$$TMP"; \
	if cmp -s "$$TMP" docs/openapi.json; then \
		printf '    [ok]   docs/openapi.json is byte-identical to the freshly fetched spec\n'; \
		exit 0; \
	fi; \
	printf '\n==> docs/openapi.json drifts from the live spec:\n'; \
	diff -u docs/openapi.json "$$TMP" | head -200 || true; \
	printf '\n==> Classifier (scripts/openapi-diff.py):\n'; \
	python3 scripts/openapi-diff.py docs/openapi.json "$$TMP"


.PHONY: dev
dev: ## Bring up docker + run backend and frontend together
	@python3 $(SCRIPT) dev

.PHONY: typecheck
typecheck: ## Typecheck the frontend packages
	@cd frontend && pnpm typecheck

.PHONY: lint
lint: ## Run all lint/format checks (backend Spotless + frontend Biome + kanban check); non-zero exit on any failure
	@python3 $(SCRIPT) lint all

.PHONY: lint-backend
lint-backend: ## Backend format check (spotlessCheck on dsl-platform, dsl-starter, dsl-plugins)
	@python3 $(SCRIPT) lint-backend

.PHONY: lint-frontend
lint-frontend: ## Frontend lint (Biome)
	@python3 $(SCRIPT) lint-frontend

.PHONY: lint-kanban
# Append-only guard: `make lint-kanban` runs check mode; `python3 scripts/cbs_cli.py lint-kanban --base <ref>`
# runs guard mode — it fails if any kanban row ID present at <ref> was dropped since, UNLESS a commit in the
# range carries the literal `[kanban-rewrite]` trailer (explicit escape hatch for deliberate pruning).
lint-kanban: ## Check docs/kanban.md (unique IDs, valid Status, resolvable Plan File, column count); --base <ref> = append-only guard
	@python3 $(SCRIPT) lint-kanban

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

# -----------------------------------------------------------------------------
# Kanban CLI — Markdown AST manipulation via unified/remark
# -----------------------------------------------------------------------------
KANBAN_FILE       ?= docs/kanban.md
KANBAN_SCRIPT     := scripts/kanban/kanban-cli.js

# Defaults for `make kanban-add`
# DESCRIPTION is constrained to 128 characters. Longer values are accepted,
# trimmed to 125, and suffixed with '...'; a warning is logged when trimming.
# Optional fields are only forwarded when explicitly set; the CLI supplies the
# documented defaults (Priority=Low, Owner=loop, Blocks/BlockedBy/Plan=-).
TITLE             ?=
ID                ?=
PRIORITY          ?=
OWNER             ?=
BLOCKS            ?=
BLOCKED_BY        ?=
PLAN              ?=
DESCRIPTION       ?=

.PHONY: kanban-install
kanban-install: ## Install kanban CLI Node dependencies
	@cd scripts/kanban && pnpm install --silent

.PHONY: kanban-list
kanban-list: kanban-install ## List kanban tasks
	@node $(KANBAN_SCRIPT) list "$(KANBAN_FILE)"

.PHONY: kanban-next
kanban-next: kanban-install ## Show next executable kanban task (highest priority, unblocked backlog)
	@node $(KANBAN_SCRIPT) next "$(KANBAN_FILE)"

.PHONY: kanban-start
kanban-start: kanban-install ## Mark next executable kanban task as In Progress
	@node $(KANBAN_SCRIPT) next "$(KANBAN_FILE)" --start

.PHONY: kanban-status
kanban-status: kanban-install ## Change task status: ID=... STATUS=...
	@test -n "$(ID)" || (echo "ID is required" && exit 1)
	@test -n "$(STATUS)" || (echo "STATUS is required" && exit 1)
	@node $(KANBAN_SCRIPT) status --id "$(ID)" --status "$(STATUS)" "$(KANBAN_FILE)"

.PHONY: kanban-show
kanban-show: kanban-install ## Show task details: ID=... [WITH_PLAN=1]
	@test -n "$(ID)" || (echo "ID is required" && exit 1)
	@node $(KANBAN_SCRIPT) show --id "$(ID)" $(if $(WITH_PLAN),--with-plan) "$(KANBAN_FILE)"

.PHONY: kanban-add
kanban-add: kanban-install ## Add/update task: ID=... TITLE=... [PRIORITY=... OWNER=... BLOCKS=... BLOCKED_BY=... PLAN=... DESCRIPTION=...]
	@test -n "$(TITLE)" || (echo "TITLE is required" && exit 1)
	@node $(KANBAN_SCRIPT) add "$(KANBAN_FILE)" \
		$(if $(ID),--id "$(ID)") \
		--title "$(TITLE)" \
		$(if $(PRIORITY),--priority "$(PRIORITY)") \
		$(if $(OWNER),--owner "$(OWNER)") \
		$(if $(BLOCKS),--blocks "$(BLOCKS)") \
		$(if $(BLOCKED_BY),--blockedBy "$(BLOCKED_BY)") \
		$(if $(PLAN),--plan "$(PLAN)") \
		$(if $(DESCRIPTION),--description "$(DESCRIPTION)")

.PHONY: kanban-clean
kanban-clean: kanban-install ## Remove all Done tasks from the kanban table
	@node $(KANBAN_SCRIPT) clean "$(KANBAN_FILE)"

.PHONY: kanban-test
kanban-test: kanban-install ## Run sandbox tests on kanban.test.md
	@set -e; \
	TMP=$$(mktemp /tmp/kanban-test.XXXXXX.md); \
	cp docs/kanban.test.md "$$TMP"; \
	echo "==> list"; node $(KANBAN_SCRIPT) list "$$TMP"; \
	echo "==> next (current)"; node $(KANBAN_SCRIPT) next "$$TMP"; \
	echo "==> status T2 Done"; node $(KANBAN_SCRIPT) status --id T2 --status Done "$$TMP"; \
	echo "==> next (unblocked)"; node $(KANBAN_SCRIPT) next "$$TMP"; \
	echo "==> start next"; node $(KANBAN_SCRIPT) next "$$TMP" --start; \
	echo "==> add"; node $(KANBAN_SCRIPT) add "$$TMP" --title "Sandbox add" --priority Low; \
	echo "==> clean"; node $(KANBAN_SCRIPT) clean "$$TMP"; \
	echo "==> final list"; node $(KANBAN_SCRIPT) list "$$TMP"; \
	rm "$$TMP"; \
	echo "kanban-test OK"

# -----------------------------------------------------------------------------
# Git helpers — worktree / branch management per task
# -----------------------------------------------------------------------------
TASK_ID ?=
WORKTREE_DIR ?= ../cbs-nova-$(TASK_ID)
BRANCH_NAME ?= feat/$(TASK_ID)

.PHONY: task-start
task-start: ## Create worktree + branch for a task: make task-start TASK_ID=T123
	@test -n "$(TASK_ID)" || (echo "TASK_ID is required" && exit 1)
	@test ! -d "$(WORKTREE_DIR)" || (echo "Worktree $(WORKTREE_DIR) already exists" && exit 1)
	git worktree add "$(WORKTREE_DIR)" -b "$(BRANCH_NAME)"

.PHONY: task-merge
task-merge: ## Merge task branch, remove worktree + branch: make task-merge TASK_ID=T123
	@test -n "$(TASK_ID)" || (echo "TASK_ID is required" && exit 1)
	git merge "$(BRANCH_NAME)"
	git worktree remove "$(WORKTREE_DIR)"
	git branch -d "$(BRANCH_NAME)"

.PHONY: task-abort
task-abort: ## Force-remove worktree + delete branch on failure: make task-abort TASK_ID=T123
	@test -n "$(TASK_ID)" || (echo "TASK_ID is required" && exit 1)
	git worktree remove --force "$(WORKTREE_DIR)"
	git branch -d "$(BRANCH_NAME)" || true

.PHONY: task-status
task-status: ## List worktrees and matching branches for TASK_ID
	@test -n "$(TASK_ID)" || (echo "TASK_ID is required" && exit 1)
	@echo "== worktrees =="; git worktree list | grep "$(TASK_ID)" || true
	@echo "== branches =="; git branch | grep "$(BRANCH_NAME)" || true
