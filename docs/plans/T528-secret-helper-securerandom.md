# T528 — `SecretHelper` — cryptographic random values via `SecureRandom`

- **Tier:** backend
- **Status:** Backlog (stub — refine at execution time)

## Goal

`RandomHelper`'s own javadoc flags the gap: *"NOT for secrets, tokens, or any security-sensitive
use case. For cryptographic RNG use a dedicated helper that wraps `SecureRandom`."* No such helper
exists (verified — `helper/` package listing has no `SecureRandom`/`Secret`-named class). DSL
authors needing an API key, signing secret, session token, or idempotency key today have only
`RandomHelper` (non-cryptographic) or `UuidV7Helper` (time-ordered, not opaque/secret-grade).

## Current state

- `helper/RandomHelper.java` — mirror-worthy pattern: `@Helper(name="random")`, mode-dispatch
  `Executable<RandomIn, RandomOut>`, `switch` on lowercased `mode`, `Result.success`/`failure`.
- `helper/Base64Helper.java`, `HexHelper.java` — existing encoding helpers to reuse for output
  formatting rather than reinventing encoding logic.
- `helper/Sha256Helper.java`, `HmacSha256*Helper.java` — existing crypto-helper precedent for
  package conventions, exception handling, and helpers.md documentation style.
- `docs/dsl/helpers.md` + `HelperDocsCoverageTest` — every registered `@Helper` must have a
  documented section, enforced by test; 4 fixture helpers are deliberately excluded (see
  `HelperDocsCoverageTest` — don't touch that exclusion list).

## Approach

1. New `SecretHelper` (`@Helper(name="secret")`), `Executable<SecretIn, SecretOut>`.
2. Backed by a single static/shared `java.security.SecureRandom` instance (not
   `ThreadLocalRandom`).
3. Modes, mirroring `RandomHelper`'s mode-dispatch shape:
   - `"bytes"` — `length` random bytes, output `hex` and/or `base64url` encoded (reuse
     `HexHelper`/`Base64Helper` encoding logic rather than duplicating it, if cleanly reusable —
     otherwise inline the same encoding, don't create a shared dependency that complicates either
     helper's module boundary).
   - `"token"` — convenience alias producing a URL-safe opaque string of `length` characters from
     a high-entropy charset (document the charset choice + effective entropy per character).
4. Bounds/validation mirroring `RandomHelper` (e.g. `length` in a sane range, reject
   negative/absurd values with the same `IllegalArgumentException` + `Result.failure` idiom).
5. `SecretIn`/`SecretOut` records in `helper/model/`, javadoc styled like `RandomIn`.
6. `docs/dsl/helpers.md` section documenting modes, params, and the explicit "cryptographic RNG,
   safe for secrets/tokens" framing (contrast with `random` helper's doc).
7. Tests: each mode happy path, invalid-length rejection, output format correctness (valid
   hex/base64url), a statistical sanity check only if the codebase already has precedent for that
   (don't invent flaky randomness tests — check `RandomHelperTest` for the existing pattern first).

## Acceptance criteria

- [ ] `SecretHelper` registered, backed by `SecureRandom`.
- [ ] `docs/dsl/helpers.md` documents it; `HelperDocsCoverageTest` green.
- [ ] Tests cover each mode + invalid input.
- [ ] `:starter:test` green, `make lint` passes.

## Files to create/modify (best guess)

- New: `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/helper/SecretHelper.java`
- New: `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/helper/model/SecretIn.java`
- New: `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/helper/model/SecretOut.java`
- New: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/helper/SecretHelperTest.java`
- Modify: `docs/dsl/helpers.md`

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test
make lint
```

## Out of scope

- Changing `RandomHelper` itself, JWT signing changes (`JwtHelper` untouched), key-derivation
  functions (KDF/PBKDF2/Argon2 — separate concern if ever needed).
