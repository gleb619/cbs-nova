# T632 — Dependabot config (gradle, pnpm, actions, docker)

## Goal

Add `.github/dependabot.yml` covering all dependency ecosystems in the repo: Gradle (backend
multi-build), pnpm (frontend workspace), GitHub Actions workflows, Docker (app/Dockerfile +
compose).

## Why

Dependency updates are fully manual today — no dependabot/renovate config exists. Version
catalogs (T597, T606) centralize versions but nothing prompts updates or surfaces CVEs.
Dependabot is free, native, and low-noise when grouped.

## Acceptance criteria

- [ ] Ecosystems configured: `gradle` (root + per-build dirs as needed — check which dirs have
      version catalogs/build files; may need several entries), `npm` with `pnpm` package
      ecosystem for `frontend/`, `github-actions` for `.github/workflows`, `docker` for `app/`.
- [ ] Weekly cadence, grouped updates enabled where supported to cut PR noise.
- [ ] Reviewers/labels set to route PRs sensibly (e.g. label `dependencies`).
- [ ] Config validated (yaml lint / dependabot schema) — no runtime check possible, so at least
      `yamllint`-clean and matching documented schema.
- [ ] Commit notes: Gradle multi-build directory entries are the likely gotcha — verify each
      directory entry points at a real build (the repo has ~6 wrappers per CLAUDE.md).

## Tier

`backend`

## Files to create/modify

- Create: `.github/dependabot.yml`

## Build/test commands

```bash
python3 -c "import yaml,sys; yaml.safe_load(open('.github/dependabot.yml'))"
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Config only — no dependency bumps in this task.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
