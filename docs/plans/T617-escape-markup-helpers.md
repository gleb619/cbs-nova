# T617 — escapeMarkup helpers (`htmlEscape` + `xmlEscape` @Helper)

## Goal

Add two `@Helper`s to the `starter` module — `HtmlEscapeHelper` and `XmlEscapeHelper` — exposing
`htmlEscape(text)` and `xmlEscape(text)` to DSL authors, so untrusted values interpolated into
HTML/XML/report output cannot break markup or inject content.

## Why

The helper family (~50 helpers) covers encode (`urlEncode`, `base64`), hash, mask, format — but has
no markup-escape primitive. DSL-generated explain reports and messages that interpolate runtime data
currently have no safe-escaping tool.

## Acceptance criteria

- [ ] `HtmlEscapeHelper` escapes `& < > " '` per HTML5 text-content rules.
- [ ] `XmlEscapeHelper` escapes `& < > "` (and optionally `'` as `&apos;`) per XML 1.0.
- [ ] Null/empty inputs handled per existing helper conventions (check sibling helpers).
- [ ] Unit tests for both, following an existing sibling test (e.g. `UrlEncodeHelperTest`) — happy
      path, all special chars, empty, non-ASCII passthrough.
- [ ] Helpers registered wherever siblings register (same auto-configuration path).
- [ ] `make lint` (Spotless) passes.

## Tier

`backend`

## Files to create/modify

- Create: `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/helper/HtmlEscapeHelper.java`
- Create: `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/helper/XmlEscapeHelper.java`
- Create: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/helper/HtmlEscapeHelperTest.java`
- Create: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/helper/XmlEscapeHelperTest.java`
- Possibly: registration site discovered via `codegraph_callers`/context on a sibling (e.g. `UrlEncodeHelper`).

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test --tests '*EscapeHelperTest'
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Follow the exact structure of an existing sibling helper — no new patterns.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
