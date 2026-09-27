# T674 — Unit spec for DefinitionBundleController

Load skills `caveman` and `codegraph` on work start.

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.

## Goal

`DefinitionBundleController` (`backend/dsl-plugins/dsl-builder/src/main/java/cbs/nova/dsl/builder/controller/DefinitionBundleController.java`,
33 LOC) — `GET /api/dsl/definitions/export` and `POST /api/dsl/definitions/import` — has zero
direct test coverage. `DraftService` (which it delegates to) is well tested via `DraftServiceTest`
/ `DefinitionBundleServiceTest`, but the controller's own param mapping is not: `include=drafts`
string-equality gate on `export`, and the `dryRun` query param defaulting to `false` on `import`.

## Acceptance Criteria

- New `DefinitionBundleControllerTest.java` in
  `backend/dsl-plugins/dsl-builder/src/test/java/cbs/nova/dsl/builder/controller/`, mocking
  `DraftService`.
- Cover `export`: no `include` param → `exportBundle(false)`; `include=drafts` →
  `exportBundle(true)`; any other `include` value → `exportBundle(false)`.
- Cover `import`: body forwarded verbatim to `importBundle(bundle, dryRun)`; `dryRun` query param
  omitted → defaults to `false`; `dryRun=true` → forwarded as `true`.
- Cover the `IOException` declared on `importBundle` propagating (no swallowing).

## Tier

`backend`

## Files to create/modify (best guess)

- `backend/dsl-plugins/dsl-builder/src/test/java/cbs/nova/dsl/builder/controller/DefinitionBundleControllerTest.java`

## Build/Test Commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-plugins :dsl-builder:test --tests "*DefinitionBundleControllerTest*"
backend/dsl-platform/gradlew -p backend/dsl-plugins :dsl-builder:test
```

## Retry Notes (2026-09-27)

Plan's target file `backend/dsl-plugins/dsl-builder/src/main/java/cbs/nova/dsl/builder/controller/DefinitionBundleController.java`
does not exist — confirmed via direct file check and repo-wide grep for `exportBundle`/`importBundle`.
The export/import-bundle feature has since relocated to `DslDraftHandler`
(`backend/dsl-starter/starter/src/main/java/cbs/nova/starter/controller/DslDraftHandler.java`, functional
router-style handler, not a Spring MVC `@Controller`) wired via `DslDefinitionBundleRouterConfiguration`,
using `VcsModels.DefinitionBundle`/`ImportBundleResult` — a different module (`dsl-starter`, not
`dsl-plugins/dsl-builder`) and a different handler shape (`ServerRequest`/`ServerResponse`, not
`@GetMapping`/`@PostMapping`).

Additionally, this exact area (`DslDraftHandler`, `VcsModels`, drafts-as-git-changes) had foreign
uncommitted/staged changes from a concurrent session in the main checkout at the time this task ran —
consistent with an active parallel refactor in flight (see T683's "drafts migration to git-changed
files" scope). Writing a unit spec against either the stale plan target or the in-flight handler risks
being invalidated within the same session.

**Blocked**, not reattempted — re-plan against the current `DslDraftHandler` shape once the concurrent
drafts/VCS refactor lands, or split into a narrower task scoped to whatever export/import surface
survives it.
