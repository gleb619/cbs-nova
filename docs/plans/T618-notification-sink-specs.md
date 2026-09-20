# T618 — Notification sink specs (Email/Webhook/GenericJson RuleSink)

## Goal

Add unit specs for the three `NotificationSink` implementations — `EmailRuleSink`,
`WebhookRuleSink`, `GenericJsonRuleSink` — pinning payload shape, error handling, and rule
matching per sink.

## Why

`notification` package has 8 main classes but only engine/repository integration tests; the three
delivery sinks (external side effects: email, webhook POST, generic JSON POST) have zero direct
specs. Regression in payload building or failure handling would surface only in production
delivery.

## Acceptance criteria

- [ ] `EmailRuleSinkTest` — recipient/subject/body assembly from a fired rule; failure path
      (transport throws) surfaces without swallowing.
- [ ] `WebhookRuleSinkTest` — correct URL/headers/body for a matching rule; non-2xx response
      handled per current behavior (pin whatever it does today).
- [ ] `GenericJsonRuleSinkTest` — JSON payload fields, content-type, failure path.
- [ ] Tests use mocks/fakes consistent with sibling specs (see `NotificationRuleEngineIntegrationTest`
      for fixtures); no real network.
- [ ] `make lint` (Spotless) passes; targeted test run green.

## Tier

`backend`

## Files to create/modify

- Create: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/notification/EmailRuleSinkTest.java`
- Create: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/notification/WebhookRuleSinkTest.java`
- Create: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/notification/GenericJsonRuleSinkTest.java`
- Read first: the three sink classes + `NotificationSink` contract.

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test --tests 'cbs.nova.starter.notification.*'
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Characterization style: pin current behavior, do not change sink logic in this task.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
