# T529 — Direct unit tests for `CsvSupport`, `YamlSupport`, `HmacSha256Support`

- **Tier:** backend
- **Status:** Backlog (stub — refine at execution time)

## Goal

Three shared helper-support classes carry real parsing/formatting/crypto logic but have zero
dedicated test file — only indirect coverage through the `@Helper` classes that call them (mirrors
the gap T501/T502 already fixed for `dsl-builder`/`starter` classes).

## Current state

- `helper/CsvSupport.java` (149 L) — shared by `FormatCsvHelper` + `ParseCsvHelper`. No
  `CsvSupportTest`.
- `helper/YamlSupport.java` (74 L) — shared by `FormatYamlHelper` + `ParseYamlHelper`. No
  `YamlSupportTest`.
- `helper/HmacSha256Support.java` (60 L) — shared by `HmacSha256SignHelper` +
  `HmacSha256VerifyHelper`. No `HmacSha256SupportTest`.
- Each is only exercised indirectly today via `FormatCsvHelperTest`/`ParseCsvHelperTest`/
  `FormatYamlHelperTest`/`ParseYamlHelperTest`/`HmacSha256SignHelperTest`/
  `HmacSha256VerifyHelperTest` — edge cases in the shared logic itself (malformed input, encoding
  edge cases, key-length handling) may be under-exercised by any single caller's happy-path test.

## Approach

1. Read each support class fully first — test its actual public surface, not a guessed one.
2. `CsvSupportTest` — round-trip parse/format, quoting/escaping edge cases (embedded delimiter,
   quote, newline), empty input, header handling per whatever the class's contract is.
3. `YamlSupportTest` — round-trip parse/format, nested structures, scalar type coercions, empty
   input.
4. `HmacSha256SupportTest` — sign/verify round-trip, wrong-key rejection, empty/short key
   handling, known-answer test vector if one is easy to pin (RFC 4231 HMAC-SHA256 test vectors).
5. No production code changes — pure test addition.

## Acceptance criteria

- [ ] Three new test files, one per support class.
- [ ] Each covers the class's real edge cases (verify against actual implementation, not this
      stub's guesses).
- [ ] `:starter:test` green, `make lint` passes.

## Files to create/modify (best guess)

- New: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/helper/CsvSupportTest.java`
- New: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/helper/YamlSupportTest.java`
- New: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/helper/HmacSha256SupportTest.java`

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test
make lint
```

## Out of scope

- `MathModeExplanation` (tied to `MathHelper`, slated for retirement in T521 — don't add tests to
  code about to be deleted).
- Any production code change to the three support classes or their calling helpers.
