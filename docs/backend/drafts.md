# DSL Drafts

Status: **normative spec + as-is reference**. Sections marked **(target)** are the agreed model
that the implementation converges to. Sections marked **(as-is)** describe the code on `main` at
the time of writing. Where they disagree, the gap is listed in [Gap analysis](#7-gap-analysis).

Related: [Backend architecture](../architecture-backend.md) ·
[Runtime Engine — route table](../dsl/runtime.md#route-table) ·
[Configuration reference](../dsl/configuration.md) ·
[Runbook — approval gate](../runbook.md#publish-via-the-approval-gate)

---

## 1. What a draft is

A **draft** is an uncommitted change to a **DSL source file** (a compact Java file that uses the
declarative DSL, e.g. `LoanDisbursementDsl.java`) in the server-side **DSL workspace**. The
workspace is a git working tree. A draft therefore has exactly the status git gives the file
relative to `HEAD`:

- **added**: new DSL file that `HEAD` does not have
- **modified**: `HEAD` has the file and the working tree has different content
- **deleted**: `HEAD` has the file and the working tree does not
- **untracked**: new file that is not yet staged (shown as *added* in the UI)
- **conflicting**: unmerged path after a pull or rebase

A DSL file with no git change is **not** a draft. Its state is **published** (committed, in
`HEAD`).

End users never edit files directly. They edit, build and compile drafts through the admin UI's
**DSL Workbench**. The Workbench calls the Spring Boot **starter**, which delegates file and git
work to the **dsl-builder** service that owns the workspace.

### Terminology

| Term | Meaning |
|------|---------|
| DSL file | A `*.java` source under the workspace's DSL source root, compiled by the DSL annotation processor into Temporal workflows/activities. |
| Definition | A named DSL construct (process / transaction / function) declared in a DSL file. One file usually holds one definition; the definition `name` is the Workbench key. |
| Workspace | Directory on the server or in a Docker volume where dsl-builder keeps DSL sources: `cbs.dsl.builder.workspace-dir`, or `cbs.dsl.builder.source-dir` when that is set. |
| Worktree | The git working tree that holds the workspace. It is either a clone of `cbs.dsl.builder.git.repo-url`/`branch`/`sub-path`, or a pre-provisioned repo at `cbs.dsl.builder.git.repository-dir`. Per-session worktrees go under `git.worktrees-dir`. |
| Draft | A DSL file whose git status is not clean (see above). |
| Published | The DSL file content at `HEAD`, which is also what the runtime has loaded (see §4.5). |
| Working set | The list of all definitions with their status (`GET /api/dsl/working-set`), which drives the Workbench tree badges. |
| Pending write | A file write that dsl-builder accepted (`202`) and buffered, but has not yet flushed to disk (see §5.3). |

---

## 2. Topology (target)

```
 Browser ──► Nuxt BFF (/api/v1/dsl/*) ──► starter (Spring Boot, :8090)
                                              │   DslDraftHandler / DslFileHandler
                                              │   DslBuilderClient (Resilience4j queue+bulkhead+breaker)
                                              ▼
                                        dsl-builder (:8091)
                                              │   FileService · DraftService · GitStatusService · CompileService
                                              ▼
                              workspace dir  ==  git worktree  (host folder / docker volume)
                                              │
                                              ▼
                                        origin repo (git remote)
```

Rules:

1. **dsl-builder is the only component that writes the workspace.** The starter never touches
   files when a `DslBuilderClient` bean is present (`csb.dsl.builder-client.enabled=true`, the
   default). Every handler branches on `builderClient() != null` and delegates.
2. The starter's local-filesystem branch (`cbs.dsl.source-dir`) exists only for single-process
   development without a builder. It must keep the same observable behaviour.
3. Git is the source of truth for draft status. No side database or marker file may contradict
   `git status`.

---

## 3. Status model (target)

### 3.1 Git → draft status

| JGit `Status` set | Porcelain | Draft status | UI badge |
|-------------------|-----------|--------------|----------|
| *(none)* | ` ` | `PUBLISHED` | none |
| `getAdded()` | `A ` | `ADDED` | **A** |
| `getUntracked()` | `??` | `ADDED` (untracked) | **A** |
| `getChanged()` (staged modify) | `M ` | `MODIFIED` | **M** |
| `getModified()` (unstaged modify) | ` M` | `MODIFIED` | **M** |
| `getRemoved()` (staged delete) | `D ` | `DELETED` | **D** |
| `getMissing()` (unstaged delete) | ` D` | `DELETED` | **D** |
| `getConflicting()` | `UU` etc. | `CONFLICTING` | **!** |

A path can appear in more than one JGit set (for example, staged as added and then edited again).
Precedence, highest first: `CONFLICTING > DELETED > ADDED > MODIFIED`. Staged and unstaged changes
are **not** separate states for the end user. The Workbench shows one status per file.

Renames show up as `DELETED(old) + ADDED(new)`. The index does not detect renames.

### 3.2 Definition status

A definition inherits the status of the DSL file that declares it. Mapping a definition name to a
file uses `GET /api/dsl/files/by-name/{name}` (starter `DslFileHandler.readByName`).

### 3.3 Lifecycle

```
            edit (write file)                     publish (commit + reload)
 PUBLISHED ───────────────────► MODIFIED ─────────────────────────────► PUBLISHED'
     │                             │  ▲
     │ delete file                 │  │ edit
     ▼                             ▼  │
  DELETED ──publish──► (gone)   discard (checkout HEAD) ──► PUBLISHED

 (none) ──create file──► ADDED ──publish──► PUBLISHED
                            └──discard (remove file)──► (none)
```

| Operation | Git effect | Runtime effect |
|-----------|------------|----------------|
| **Save** (edit) | write working-tree file (buffered, then flushed) | none |
| **Validate / compile** | none | isolated compile in a builder session. Diagnostics are returned, and nothing is loaded. |
| **Discard** | `checkout HEAD -- <path>`, or remove an untracked file | none |
| **Publish** | after a successful reload, `add` + `commit` of the definition's source path (author = actor). Push is not implemented yet. | reload first. If the reload fails, nothing is committed: the draft stays, and diagnostics are returned (§4.5). |
| **History** | `git log -- <path>` | none |
| **Restore(rev)** | `checkout <rev> -- <path>`, which produces a MODIFIED draft | none, until publish |

Invariants:

- **I1.** Draft status is always derived from `git status`. It is never stored.
- **I2.** Publish is atomic per request. Either every file in the request is committed, or none is.
- **I3.** Only files under the DSL source root count. Build outputs (`build/`, `.gradle/`) and
  `.workbench/` are ignored via `.gitignore`.
- **I4.** Paths are workspace-relative and forward-slash. `\` is normalised to `/`, leading `/`
  is stripped, and anything containing `..` is rejected (`FileController.sanitizePath`).
- **I5.** A pending (unflushed) write is already a draft from the user's point of view.
  Status/working-set endpoints must flush first or merge the pending buffer.
- **I6.** The publish approval gate (`cbs.dsl.approval.required`, T568) applies unchanged. A
  change request snapshots the draft files and approval replays the publish.

---

## 4. Current implementation (as-is)

Today "draft" has **two parallel representations** that are not yet unified.

### 4.1 JSON draft markers (Workbench drafts API)

`DslDraftHandler` (starter) and `DraftService` (dsl-builder) store a `DraftRequest` as
pretty-printed JSON:

```
<workspace>/
  .workbench/drafts/<safeName>.json      ← status "Draft"       (save)
  .workbench/published/<safeName>.json   ← status "Published"   (publish / restore / import)
  .workbench/history/<safeName>/<epochMs>.json  ← snapshot taken before each publish
```

- `safeName = name.replaceAll("[^A-Za-z0-9._-]", "_")`
- `DraftRequest` = `{ name, type, status, version, taskQueue, source, savedAt }`. `source`
  carries the DSL text.
- **save** stamps `savedAt` and writes the draft marker.
- **publish**: `historyService.snapshotBeforePublish`, then write the published marker, then
  `reloadHandler.reloadDefinitions()`, then delete the draft marker on success (starter side).
  Through the builder, `DraftService.publish` writes the marker and deletes the draft. The
  starter then runs `finishPublish` (reload + diagnostics).
- **restore(ts)** re-publishes a history snapshot.
- **delete** removes only the draft marker.
- **export/import bundle** copies published markers (`DefinitionBundle`, digest-verified,
  max 200 definitions).

Starter directory constants are in `StarterConstants.WORKBENCH_{DRAFTS,PUBLISHED,HISTORY}_DIR`.
The builder equivalents are `cbs.dsl.builder.workbench.{drafts,published,history}-dir`.

### 4.2 DSL file API (real source files)

`DslFileHandler` (starter) → `DslBuilderClient` → `FileController`/`FileService` (builder):

- Writes are **staged** into `FileBuffer` and return `202`. A scheduled flusher
  (`cbs.dsl.builder.files.flush-interval-seconds`, default 5) writes them to disk.
  `POST /api/dsl/files/flush` forces a flush. `GET /api/dsl/files/status` returns the pending
  count.
- Reads go through `BuilderCache` on the starter side. The builder serves reads from the
  pending buffer first, then disk.

These are the files git sees. Editing a DSL file through this API **is** a draft in the target
sense.

### 4.3 Git status

`GitStatusService` (builder, exposed at `GET /api/dsl/vcs/status`) and `DslGitStatusResolver`
(starter; delegates to `DslBuilderClient.vcsStatus()` when a builder is present, otherwise runs
JGit locally) return:

```json
{
  "workTree": "/abs/path",
  "dirtyPaths": ["dsl/LoanDsl.java", "dsl/NewDsl.java"],
  "changes": { "dsl/LoanDsl.java": "MODIFIED", "dsl/NewDsl.java": "UNTRACKED" }
}
```

`changes` maps each path to a `ChangeType` (`ADDED`, `MODIFIED`, `DELETED`, `UNTRACKED`,
`CONFLICTING`), classified per §3.1 by the static `classify(Status)` method. `dirtyPaths` is its
key set and is kept for older peers. A payload without `changes` still parses, and `changes` is
then empty. `RepoStatus.changeOf(path)` looks up one path. Results are cached per repo root for
`git.status-cache-ttl-seconds` (default 5). `GitStatusService.invalidate()` clears the cache.

Freshness (I5):

- `VcsController.status` flushes pending `FileService` writes before it scans.
- `FileService.flushPending` and every `DraftService` write invalidate the status cache.
- On the starter side, `DslBuilderClient` invalidates the matching `BuilderCache` entries after
  every successful mutating call: draft, files, VCS status and pending count.

### 4.4 Definition status resolution

`DslDefinitionStatusResolver.resolveAll(names)` feeds `/api/dsl/working-set` and other
introspection endpoints. It uses
`DefinitionStatus { PUBLISHED, DRAFT, MODIFIED, ADDED, DELETED, CONFLICTING }`.

1. `DslSourcePathResolver.relativePath(name)` maps the definition to its DSL source file. It
   uses `GlobalManager.findFilename`, then a recursive lookup under `cbs.dsl.source-dir`.
   `DslFileHandler` shares this resolver.
2. If git is available, the path is looked up in `RepoStatus.changes()` with suffix-tolerant
   matching: a change key `K` matches path `P` if `K == P`, `K` ends with `/P`, or `P` ends with
   `/K`. This is needed because the builder's worktree root can differ from the starter's
   source dir. `ADDED|UNTRACKED → ADDED`, `MODIFIED → MODIFIED`, `DELETED → DELETED`,
   `CONFLICTING → CONFLICTING`.
3. If there is no git change, the legacy check applies: `.workbench/drafts/<safeName>.json`
   exists → `DRAFT`.
4. Otherwise → `PUBLISHED`.

The admin UI shows chips for the new values (`ConstructStatus` in
`frontend/components/src/types/dsl.ts`, `PlainConstructList.vue`).

### 4.5 Publish result contract (keep)

`DraftResponse { name, status, location, reloaded, loadResult, reloadError, diagnostics[≤20], savedAt, commitId }`.
A publish whose reload fails still returns `200` with `reloaded=false`, `reloadError` and
`diagnostics`. The diagnostics are persisted via `CompileDiagnosticRecordRepository` (source
`PUBLISH`). Audit actions: `DRAFT_WRITE`, `DEFINITION_PUBLISH`, `DRAFT_BULK_WRITE`. Domain events:
`DraftSaved`, `DraftPublished`. They are best-effort; a failure is logged and swallowed.

Git commit on publish (builder mode, `DslDraftHandler.publishPayload`) works like this. When the
reload succeeds and the definition's source path has a git change, the starter calls
`DslBuilderClient.commit` with message `Publish <name>`. The author is the audit actor, with
email `<actor>@cbs-nova.local`. The resulting `commitId` is returned in `DraftResponse`. When the
reload fails, nothing is committed. A commit failure is logged, and the audit entry records
`commitError`. The publish still returns 200, with `commitId: null`.

---

## 5. How to work with drafts (as-is API)

All starter paths are also exposed by the BFF under `/api/v1/dsl/...` (explicit Nitro routes in
`frontend/admin-ui-plugin/server/api/v1/dsl/`, no catch-all).

### 5.1 Starter endpoints

| Method     | Path                                                                        | Handler                              | Notes |
| ---------- | --------------------------------------------------------------------------- | ------------------------------------ | ----- |
| GET        | `/api/dsl/drafts?limit&offset`                                              | `DslDraftHandler.list`               | `PageResponse<DraftSummary>`. Returns an empty page when the source dir is not configured. |
| GET        | `/api/dsl/drafts/{name}`                                                    | `read`                               | `DraftRequest`, 404 if missing |
| POST       | `/api/dsl/drafts/{name}/save`                                               | `save`                               | body `DraftRequest` (name required) |
| POST       | `/api/dsl/drafts/{name}/publish`                                            | `publish`                            | 403 when the approval gate is on and caller < OPERATOR |
| POST       | `/api/dsl/drafts/{name}/change-request`                                     | `ChangeRequestHandler.submit`        | approval-gate path |
| DELETE     | `/api/dsl/drafts/{name}`                                                    | `delete`                             | deletes the draft marker only |
| POST       | `/api/dsl/drafts/{name}/discard`                                            | `discard`                            | Builder mode only. Reverts the definition's source file to `HEAD` and drops the legacy marker (a 404 there is ignored). Returns `status: "Discarded"`. Local mode → `409 GIT_REQUIRES_BUILDER`. Unknown source path → 404. |
| GET        | `/api/dsl/drafts/{name}/commits?limit`                                      | `commits`                            | Builder mode only. `git log` of the definition's source path, newest first. |
| GET        | `/api/dsl/drafts/{name}/history`                                            | `history`                            | `DefinitionHistoryEntry[]` |
| GET        | `/api/dsl/drafts/{name}/history/{ts}`                                       | `historyEntry`                       | snapshot `DraftRequest` |
| GET        | `/api/dsl/drafts/{name}/history/{ts}/diff`                                  | `historyDiff`                        | snapshot vs current published, `LineDiff` hunks |
| POST       | `/api/dsl/drafts/{name}/history/{ts}/restore`                               | `restore`                            | re-publishes the snapshot and reloads |
| GET        | `/api/dsl/definitions/export[?include=drafts]`                              | `exportBundle`                       |  |
| POST       | `/api/dsl/definitions/import[?dryRun=true]`                                 | `importBundle`                       |  |
| GET / POST | `/api/dsl/files`, `/api/dsl/files/{*path}`, `/api/dsl/files/by-name/{name}` | `DslFileHandler`                     | list / read / stage write |
| POST       | `/api/dsl/files/bulk`, `/api/dsl/files/flush`                               | `DslFileHandler`                     | bulk stage / force flush |
| GET        | `/api/dsl/files/status`                                                     | `DslFileHandler.status`              | `{ pending }` |
| GET        | `/api/dsl/working-set`                                                      | `DslIntrospectionHandler.workingSet` | definitions + `DefinitionStatus` |

When no source dir is configured, the handlers return `409 NOT_CONFIGURED`
(`csb.dsl.source-dir is not configured`). If the dir does not exist they return
`409 NOT_FOUND`.

### 5.2 dsl-builder endpoints

`DraftController` `/api/dsl/drafts/**` (same shapes as above), `DefinitionBundleController`,
`FileController` `/api/dsl/files/**`, `VcsController` `/api/dsl/vcs/**`, `CompileController` `/api/dsl/compile` (+ `/{id}/download`). Errors use
`BuilderApiException(status, code, message)`. The starter's `DslBuilderClient` maps them back.
A full queue gives `BuilderClientBusyException`, and an open breaker or unreachable host gives
`BuilderUnavailableException`.

`VcsController` (backed by `WorkspaceGitService`, JGit):

| Method | Path | Body / params | Result |
|--------|------|---------------|--------|
| GET | `/api/dsl/vcs/status` | – | `RepoStatus`. 404 when there is no repo. |
| POST | `/api/dsl/vcs/commit` | `CommitRequest {paths, message, authorName, authorEmail}` | `CommitResult {commitId, paths, timestampMillis}`. Commits **only** the listed paths; other staged files stay staged. Atomic (I2): the index is reset on failure. 409 `NOTHING_TO_COMMIT` if any path is clean. |
| POST | `/api/dsl/vcs/discard` | `DiscardRequest {paths}` | `DiscardResult {discarded}`. MODIFIED/DELETED → checkout `HEAD`, ADDED → unstage + delete, UNTRACKED → delete, clean → skipped. |
| GET | `/api/dsl/vcs/log?path&limit` | limit default 20, clamped to 1..200 | `LogEntry[] {commitId, timestampMillis, author, message}`, newest first |
| GET | `/api/dsl/vcs/show?path&commit` | – | `{path, commitId, content}`. 404 if the path is missing at that commit or the commit is unknown. |

Commit and discard flush pending file writes first. Paths are workspace-relative. When the
workspace is a subdirectory of the repo, the service translates paths to and from repo-relative
form. A blank path or one containing `..` → 400 `INVALID_PATH`. Git disabled or no repo →
409 `GIT_NOT_CONFIGURED`.

### 5.3 Recipes

Edit a DSL file (this makes a draft in the target sense):

```bash
curl -X POST localhost:8090/api/dsl/files/dsl/LoanDisbursementDsl.java \
     -H 'Content-Type: application/json' -d '{"content":"<java source>"}'   # 202, buffered
curl -X POST localhost:8090/api/dsl/files/flush                              # force to disk
curl localhost:8091/api/dsl/vcs/status                                        # builder: dirty paths
```

Save and publish a Workbench draft (the current marker flow):

```bash
curl -X POST localhost:8090/api/dsl/drafts/LoanDisbursement/save \
     -H 'Content-Type: application/json' \
     -d '{"name":"LoanDisbursement","type":"process","version":"1.0.0","taskQueue":"loan","source":"..."}'
curl -X POST localhost:8090/api/dsl/drafts/LoanDisbursement/publish \
     -H 'Content-Type: application/json' -d '{"name":"LoanDisbursement", ...}'
# → DraftResponse; check "reloaded" and "diagnostics"
```

List history and roll back:

```bash
curl localhost:8090/api/dsl/drafts/LoanDisbursement/history
curl localhost:8090/api/dsl/drafts/LoanDisbursement/history/1727150000000/diff
curl -X POST localhost:8090/api/dsl/drafts/LoanDisbursement/history/1727150000000/restore
```

### 5.4 Docker / server setup

- Mount the DSL repo (or an empty volume plus `git.repo-url`) at `cbs.dsl.builder.workspace-dir`.
  Only dsl-builder mounts it read-write.
- Keep `cbs.dsl.builder.git.enabled=true` (the default). When the workspace is not a git repo,
  `/api/dsl/vcs/status` returns 404, `DslBuilderClient.vcsStatus()` returns empty, and every
  definition resolves to `PUBLISHED` or `DRAFT` (marker-based) only.
- Add `.workbench/`, `build/` and `.gradle/` to the repo's `.gitignore` so markers and build
  output never show up as drafts (invariant I3).

---

## 6. Configuration

| Key | Default | Owner | Purpose |
|-----|---------|-------|---------|
| `csb.dsl.drafts.enabled` | `true` | starter | Registers `DslDraftHandler`. (`docs/dsl/runtime.md` spells it `dsl.drafts.enabled`, but the code prefix is `csb.dsl.drafts`.) |
| `cbs.dsl.source-dir` | – | starter | Local-mode workspace. Required by the draft handlers even in builder mode, because they call `ensureConfigured`. |
| `cbs.dsl.git.{enabled,repository-dir,status-cache-ttl-seconds}` | –, –, 5 | starter | Local JGit status (used only without a builder) |
| `cbs.dsl.approval.required` | `false` | starter | Publish approval gate (T568) |
| `csb.dsl.builder-client.enabled` / `base-url` | `true` / `http://localhost:8091` | starter | Delegate all draft/file/vcs calls to dsl-builder |
| `cbs.dsl.builder.workspace-dir` / `source-dir` | – | builder | Workspace root (`source-dir` wins for the file API) |
| `cbs.dsl.builder.git.{enabled,repository-dir,worktrees-dir,repo-url,sub-path,branch,status-cache-ttl-seconds}` | `true`,…,5 | builder | Worktree provisioning + status |
| `cbs.dsl.builder.files.{flush-interval-seconds,max-queue-size,read/write-bulkhead-permits}` | 5, 100, 32, 8 | builder | Write buffer |
| `cbs.dsl.builder.drafts.history-limit` | 20 | builder | History snapshots kept per definition |
| `cbs.dsl.builder.workbench.*` | see `DslBuilderProperties.Workbench` | builder | Marker dirs, bundle limits, diff hunks/context |

---

## 7. Gap analysis

| # | Target | Before `feat/drafts-git` | Status |
|---|--------|-------|-------------|
| G1 | Draft = git-changed DSL file | Draft = `.workbench/drafts/*.json` marker. File edits via `/api/dsl/files` are not drafts to the drafts API. | **Mostly closed.** Status now comes from the source file (§4.4). Markers remain only as a fallback, and for the save/read/history APIs. |
| G2 | Per-file kind (A/M/D/?/U) | `RepoStatus.dirtyPaths` is a flat set | **Closed** (step 1). |
| G3 | Status of the **DSL source** file | `DslDefinitionStatusResolver` checks the git state of the **marker JSON** | **Closed** (step 2). |
| G4 | Publish = compile + commit (+ push) + reload | Publish = write marker + reload. No commit. History = JSON snapshots. | **Partly closed** (step 4). Publish commits, and `/commits` exposes `git log`. `history`/`restore` still use JSON snapshots. Push is not implemented. |
| G5 | Discard = checkout HEAD | Delete = remove marker | **Closed** (step 4): `POST /api/dsl/drafts/{name}/discard`. |
| G6 | Pending writes count as drafts (I5) | Status is read from disk only (5s cache) | **Closed** (step 3): flush-before-status plus cache invalidation. |
| G7 | One copy of status logic | `GitStatusService` (builder) and `DslGitStatusResolver` (starter) duplicate JGit code | **Open.** Both copies now carry the same `classify`; they are still duplicated. |
| G8 | Property name consistency | Draft gate `csb.dsl.drafts.*` vs docs `dsl.drafts.*` | **Open.** |

### Migration path (incremental, each step shippable)

Steps 1–4 landed on branch `feat/drafts-git`. Remaining work: step 4 history/restore on git, a one-off marker import commit, optional push, G7 and step 5.

1. **Typed status (G2, G7).** Add `changes: Map<path, ChangeType>` to `RepoStatus` and keep
   `dirtyPaths` as its key set, so the builder↔starter JSON stays backward compatible. Classify
   per §3.1. Extend `DefinitionStatus` with `ADDED`, `DELETED` and `CONFLICTING`. The frontend
   `WorkingSetEntry` type and badges must follow.
2. **Resolve by source file (G1, G3).** In `DslDefinitionStatusResolver`, map definition → DSL file
   path (manifest / `files/by-name`) and read that path's `ChangeType`. Keep the marker check as
   a fallback only when git is disabled.
3. **Flush-before-status (G6).** Have `VcsController.status` flush `FileService` (or overlay the
   pending buffer as `MODIFIED`/`ADDED`) before scanning, and invalidate the status cache on flush.
4. **Git-backed publish/discard/history (G4, G5).** Add builder endpoints
   `POST /api/dsl/vcs/commit {paths, message}`, `POST /api/dsl/vcs/discard {paths}`,
   `GET /api/dsl/vcs/log?path=`. Re-point `DraftService.publish/delete/history/restore` at them.
   Keep the `DraftResponse`/`DefinitionHistoryEntry` shapes so the BFF and UI do not change.
   Retire `.workbench/published` and `.workbench/history` after a one-off import commit.
5. **Property cleanup (G8).** Accept both prefixes for one release, then drop `csb.` and fix
   `runtime.md`.

---

## 8. Testing

### 8.1 Existing coverage

| Area | Test |
|------|------|
| Starter draft routes (local FS) | `starter/.../DslDraftResourceTest` |
| Approval gate | `DslDraftApprovalGateTest`, `approval/ChangeRequestServiceTest` |
| Bundle export/import | `DslDefinitionBundleResourceTest` |
| Builder client (HTTP contract, breaker, queue, `vcsStatus` 404→empty) | `starter/.../builder/DslBuilderClientTest` |
| Starter git status (JGit, cache TTL, builder delegation) | `starter/.../service/DslGitStatusResolverTest` |
| Definition status mapping | `starter/.../service/DslDefinitionStatusResolverTest` |
| Working set | `DslIntrospectionServiceTest`, `DslIntrospectionResourceTest` |
| Builder drafts | `dsl-builder/.../service/DraftServiceTest`, `controller/DraftControllerTest` |
| Builder files | `FileServiceTest`, `FileBufferTest`, `FileBulkheadTest`, `controller/FileControllerTest` |
| Builder git status | `GitStatusServiceTest`, `controller/VcsControllerTest` |
| Builder git ops (commit/discard/log/show) | `WorkspaceGitServiceTest`, `controller/VcsControllerTest` |
| Starter source path + status by source file | `DslSourcePathResolverTest`, `DslDefinitionStatusResolverTest` |
| Starter git-backed publish/discard/commits | `DslDraftGitResourceTest` |
| Builder cache invalidation | `builder/BuilderCacheTest`, `DslBuilderClientTest` |
| BFF discard/commits | `server/api/v1/dsl/drafts/__tests__/drafts-git.spec.ts` |
| BFF proxy | `frontend/admin-ui-plugin/server/api/v1/dsl/drafts/__tests__`, contract fixture `working-set.json` |

Run:

```bash
backend/dsl-platform/gradlew -p backend/dsl-plugins :dsl-builder:test \
  --tests '*Draft*' --tests '*GitStatus*' --tests '*Vcs*' --tests '*File*'
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test \
  --tests '*Draft*' --tests '*GitStatusResolver*' --tests '*DefinitionStatusResolver*' \
  --tests '*DslBuilderClientTest' --tests '*Introspection*'
```

### 8.2 Required tests for the target model

Each migration step in §7 ships with these tests. Use real JGit repos in `@TempDir`, as
`GitStatusServiceTest.initRepo()` already does, and no mocks for git.

| ID | Given | When | Then |
|----|-------|------|------|
| T-S1 | Committed repo; new staged file | status | `ADDED` |
| T-S2 | New unstaged file | status | `ADDED` (untracked) |
| T-S3 | Tracked file edited (staged and unstaged variants) | status | `MODIFIED` |
| T-S4 | Tracked file `git rm` / deleted on disk | status | `DELETED` |
| T-S5 | Staged new file edited again | status | `ADDED` (precedence) |
| T-S6 | Merge conflict | status | `CONFLICTING` |
| T-S7 | Builder returns legacy JSON without `changes` | starter parses | `dirtyPaths` kept, `changes` empty, no failure |
| T-S8 | File in `.workbench/` or `build/` | status | not reported (I3) |
| T-R1 | Definition file MODIFIED in git, no marker | working-set | definition status `MODIFIED` (G1/G3) |
| T-R2 | Git disabled | working-set | marker fallback (`DRAFT` / `PUBLISHED`) unchanged |
| T-F1 | File staged but not flushed | status | reported as a draft (I5) |
| T-P1 | Draft whose reload fails | publish | no commit, `commitId=null`, diagnostics returned, status unchanged |
| T-P2 | Valid draft | publish | commit exists (author = actor), status clean, `reloaded=true` |
| T-P3 | Valid draft, commit throws | publish | 200, `commitId=null`, audit detail `commitError` |
| T-P4 | Approval gate on, AUTHOR | publish | 403. Change request snapshots file content. |
| T-D1 | MODIFIED / ADDED / DELETED | discard | file back to `HEAD` / removed / restored, status clean |
| T-H1 | Two publishes | history + restore(first) | restored content is a `MODIFIED` draft, not yet committed |
| T-X1 | Path with `..` | any file/vcs op | 400 (I4). A leading `/` is stripped, not rejected. |

---

## 9. Code map

| Concern | Starter (`backend/dsl-starter/starter/.../cbs/nova/starter`) | dsl-builder (`backend/dsl-plugins/dsl-builder/.../cbs/nova/dsl/builder`) |
|---------|------|------|
| Draft HTTP | `controller/DslDraftHandler`, router config | `controller/DraftController`, `DefinitionBundleController` |
| Draft logic | inline in `DslDraftHandler` (local mode) | `service/DraftService` |
| History | `service/DslDefinitionHistoryService` | `service/DefinitionHistoryService` |
| Bundles | `service/DslDefinitionBundleService` | `service/DefinitionBundleService` |
| Files | `controller/DslFileHandler`, `DslFileService` | `controller/FileController`, `service/FileService`, `FileBuffer`, `FileBulkhead` |
| Git status | `service/DslGitStatusResolver` | `service/GitStatusService`, `controller/VcsController` |
| Git ops | via `DslBuilderClient.commit/discard/log/show` | `service/WorkspaceGitService` |
| Definition → source path | `service/DslSourcePathResolver` | – |
| Git clone / worktree | – | `service/GitService`, `RepoUrlValidator` |
| Compile | – (via `DslBuilderClient.compile`) | `controller/CompileController`, `service/CompileService`, `GradleService` |
| Definition status | `service/DslDefinitionStatusResolver`, `model/DslIntrospectionModels.DefinitionStatus` | – |
| Remote client | `builder/DslBuilderClient`, `BuilderCache`, `config/BuilderClientConfiguration` | – |
| Models | `model/VcsModels`, `model/DslFileModels` | `model/VcsModels`, `model/DslFileModels` (mirrored) |
