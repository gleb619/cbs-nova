# T628 — httpCall retry support (maxAttempts + retryBackoffMillis)

## Goal

Add opt-in retry to `HttpCallHelper`: `maxAttempts` and `retryBackoffMillis` fields on
`HttpCallIn` (null/`<=1` = single attempt, current behavior). Retry on transport failure
(IOException) and retryable statuses (5xx, 429) — exact status set to be pinned in implementation
after reading the helper's error handling.

## Why

`httpCall` is the main DSL external-call path. Timeout exists but no retry — flaky upstream APIs
force manual BackoffHelper composition per flow. Opt-in fields keep blast radius minimal: absent
fields = byte-identical behavior today.

## Acceptance criteria

- [ ] `HttpCallIn` gains nullable `maxAttempts`, `retryBackoffMillis`; defaults preserve current
      single-attempt behavior (all existing tests untouched and green).
- [ ] Retries on transport failure + retryable HTTP statuses; non-retryable statuses (4xx other
      than 429) fail fast.
- [ ] Final failure surfaces the same error shape as today (last attempt's error).
- [ ] Fixed (non-exponential) backoff between attempts, `retryBackoffMillis` clamp documented.
- [ ] Tests: flaky-then-success stub server (retry recovers), always-failing (attempts counted,
      last error surfaced), 4xx fail-fast, defaults unchanged.
- [ ] `make lint` passes; targeted test run green.

## Tier

`backend`

## Files to create/modify

- Modify: `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/helper/model/HttpCallIn.java`
- Modify: `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/helper/HttpCallHelper.java`
- Modify/extend: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/helper/HttpCallHelperTest.java`

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test --tests '*HttpCall*'
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Opt-in only — no behavior change when new fields absent.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
