# T619 — caseConvert @Helper (camel/kebab/snake/title case)

## Goal

Add `CaseConvertHelper` to the `starter` module exposing DSL-callable case conversion:
`camelCase`, `kebabCase`, `snakeCase`, `titleCase` string transforms.

## Why

Helper family (~50 helpers) has no case-conversion primitive. DSL flows mapping data between
REST (camelCase), URLs/headers (kebab), DBs/env (snake), and human reports (title) currently
need inline regex gymnastics. A `GlobalManager.toKebabCase` util exists internally (T574
relocated it) but is not DSL-callable.

## Acceptance criteria

- [ ] `CaseConvertHelper` exposes 4 modes (or 4 helpers — follow sibling naming convention, e.g.
      how `HmacSha256Sign/Verify` or `Format*` helpers structure multi-operation surfaces).
- [ ] Handles: plain words, already-cased input (`fooBar`, `foo_bar`, `foo-bar`), acronyms
      (`parseXML` → `parse_xml`), digits (`v2Api` → `v2_api`), empty/null per sibling conventions.
- [ ] Unit tests cover all modes + edge cases above.
- [ ] Reuses `toKebabCase` util where sensible instead of duplicating logic.
- [ ] Registered wherever sibling helpers register.
- [ ] `make lint` passes; targeted test run green.

## Tier

`backend`

## Files to create/modify

- Create: `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/helper/CaseConvertHelper.java` (naming per convention chosen)
- Create: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/helper/CaseConvertHelperTest.java`
- Read first: existing util from T574 (`codegraph_search toKebabCase`), sibling helper for pattern.

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test --tests '*CaseConvert*'
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Follow sibling helper structure exactly — no new patterns.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
