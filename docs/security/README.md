# Security — supply chain baseline files (T414)

This directory holds the **baseline / suppression** files consumed by the
two CVE scanners wired into `make cve-scan`. They let a real finding be
triaged once and accepted for a bounded time, so the gate stays usable
day-one instead of failing on every noisy CVE in the transitive tree.

## Files

| File                          | Scanner                       | Format                          |
| ----------------------------- | ----------------------------- | ------------------------------- |
| `cve-baseline.xml`            | OWASP dependency-check (Gradle) | dependency-check suppression XML |
| `pnpm-audit-baseline.json`    | `pnpm audit` (frontend)       | JSON, advisory-id allowlist      |

## How `make cve-scan` uses them

- **Backend** — Gradle task `:starter-launcher:dependencyCheckAnalyze`
  reads `docs/security/cve-baseline.xml` via the `suppressionFile`
  property set in `backend/dsl-starter/starter-launcher/build.gradle`.
  Suppressed findings are still listed in the report but are excluded
  from the `failBuildOnCVSS` gate.
- **Frontend** — A small bash parser inside the `make cve-scan` target
  reads `docs/security/pnpm-audit-baseline.json`, runs
  `pnpm audit --prod --json`, and exits non-zero only on `high`/`critical`
  advisories that are NOT in the allowlist.

## Adding a new suppression entry

### Backend (dependency-check)

1. Run `make cve-scan` (or `backend/dsl-platform/gradlew -p
   backend/dsl-starter :starter-launcher:dependencyCheckAnalyze`).
2. Open the HTML or JSON report under
   `backend/dsl-starter/starter-launcher/build/reports/dependency-check/`.
3. For each finding you accept, copy its `<name>` / `<gav>` / `<cpe>` /
   `<cve>` block into a new `<suppress>` element in
   `docs/security/cve-baseline.xml`. Include a `<notes>` element that
   explains *why* (link to issue, ETA, mitigation).
4. **Always set `<until>YYYY-MM-DD</until>`** — suppressions expire so
   the gate forces a re-triage.
5. Re-run `make cve-scan`. The finding should now be marked
   "suppressed" in the report.

Reference:
<https://dependency-check.github.io/DependencyCheck/suppression.html>

### Frontend (pnpm audit)

1. Run `make cve-scan`. The script prints the advisory IDs it counts as
   failures. Copy the relevant `id` field from
   `pnpm audit --prod --json` output.
2. Add a new entry to `pnpm-audit-baseline.json` under
   `allowlisted_advisories`:

   ```json
   {
     "id": "<advisory-id>",
     "package": "<npm-package-name>",
     "severity": "high|critical",
     "title": "<short title>",
     "reason": "<why this is accepted>",
     "added_at": "YYYY-MM-DD",
     "expires_on": "YYYY-MM-DD"
   }
   ```

3. **Always set `expires_on`** so the entry expires and forces a
   re-triage at least every quarter. The gate enforces it — see below.
4. Re-run `make cve-scan`. The advisory should no longer contribute to
   the failure count.

### `expires_on` semantics (enforced)

`pnpm audit` advisories are gated by
`scripts/src/cbs_cli/commands/cve_gate.py`, registered as the
`cve-scan` CLI command:

```bash
python scripts/cbs_cli.py cve-scan <audit.json> <allowlist.json> [--today YYYY-MM-DD]
```

- `expires_on` is **mandatory** for every entry in
  `allowlisted_advisories`. A missing, `null`, or unparseable value
  makes the gate fail hard rather than silently accept the entry.
- The last day is **inclusive**: an entry with `expires_on: 2026-09-30`
  still suppresses its advisory on 2026-09-30 and is considered expired
  from 2026-10-01 onward.
- An expired entry is **not** allowlisted. Its advisory counts toward
  the gate again and the output names the entry so the operator knows
  why it reappeared:
  `  - expired allowlist entry 1137043 (nuxt, expired 2026-01-31)`.
- `--today YYYY-MM-DD` overrides the reference date (used by the tests
  so nothing is time-dependent).

Exit codes:

| Code | Meaning                                                          |
| ---- | ---------------------------------------------------------------- |
| `0`  | No non-allowlisted `high`/`critical` advisories                  |
| `1`  | At least one `high`/`critical` advisory is not allowlisted       |
| `2`  | Config error — audit/allowlist unparseable, or invalid `expires_on` / `--today` |

## Graceful-degrade contract

`make cve-scan` is **explicitly not in the default `make test` path**.
When the host cannot reach the NVD feed (`services.nvd.nist.gov` /
`nvd.nist.gov/feeds/...`) or the npm advisory service
(`registry.npmjs.org/-/npm/v1/security/...`), the target degrades:

- Backend scan prints `NVD feed unavailable, skipping backend CVE scan`
  and skips the Gradle invocation. The pnpm audit still runs.
- Frontend scan prints `npm advisory service unavailable, skipping
  frontend CVE scan` and skips the pnpm invocation. The Gradle scan
  still runs.

The target exits with the highest non-zero exit code produced by the
scanners that did run. CI operators can set
`CBS_DEPENDENCY_CHECK_DISABLED=true` to skip the Gradle scan entirely
(no network at all).