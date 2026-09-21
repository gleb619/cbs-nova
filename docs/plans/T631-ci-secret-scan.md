# T631 — CI secret scan (gitleaks)

## Goal

Add a secret-scanning job to `.github/workflows/ci.yml` using gitleaks (gitleaks/gitleaks-action
or direct `gitleaks detect`), plus a minimal `.gitleaks.toml` allowlist for known test fixtures
(dummy keys, WireMock stubs, testcontainers creds).

## Why

Repo handles secrets end-to-end — `FieldEncryptor`/`AesFieldEncryptor`, `SecretHelper`,
OIDC/rate-limit properties, compose env files — but nothing scans for leaked credentials:
no gitleaks config, no CI job, no pre-commit. One pasted API key in a fixture would ship silently.

## Acceptance criteria

- [ ] New CI job runs gitleaks on full history (`gitleaks detect --no-git` on checkout or
      git-mode scan) and fails on findings.
- [ ] `.gitleaks.toml` with baseline allowlist: test-only keys (search `codegraph_search
      Secret` + grep test resources for dummy tokens).
- [ ] Verified locally: `gitleaks detect` (or docker image) exits 0 on current HEAD.
- [ ] Job runs fast (<1 min) and doesn't block on network beyond action pull.
- [ ] `make lint` unaffected.

## Tier

`backend`

## Files to create/modify

- Modify: `.github/workflows/ci.yml`
- Create: `.gitleaks.toml`

## Build/test commands

```bash
# local verify (docker):
docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:latest detect --source /repo
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Allowlist entries must each carry a comment naming the fixture they excuse.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
