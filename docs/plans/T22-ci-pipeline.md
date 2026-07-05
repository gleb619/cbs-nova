# T22 — GitHub Actions CI Pipeline

## Goal

Add a GitHub Actions workflow that builds and tests the backend on every push and pull request.

## Acceptance Criteria

- `.github/workflows/ci.yml` exists at repo root
- Triggers on `push` and `pull_request` for all branches
- Uses `actions/setup-java@v4` with Temurin 25
- Runs `./gradlew :dsl-api:build :dsl:build :dsl-codegen:build :starter:build --no-daemon` from `backend/`
- Runs `./gradlew :dsl-codegen:test :dsl:test :starter:test --no-daemon` from `backend/`
- Caches Gradle wrapper and dependencies with `actions/cache@v4`
- Reports test results via `actions/upload-artifact` if tests fail

## Files to Create

- **Create**: `.github/workflows/ci.yml` (at repo root, NOT inside `backend/`)

## Implementation Notes

```yaml
name: CI

on:
  push:
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
          cache: gradle
      - name: Build
        working-directory: backend
        run: ./gradlew :dsl-api:build :dsl:build :dsl-codegen:build :starter:build --no-daemon
      - name: Test
        working-directory: backend
        run: ./gradlew :dsl-codegen:test :dsl:test :starter:test --no-daemon
      - name: Upload test results
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: backend/**/build/reports/tests/
```

## Constraints

- File must be at `.github/workflows/ci.yml` (repo root level)
- No `Co-Authored-By` line in commit
- Commit: `feat(T22): add GitHub Actions CI pipeline`
