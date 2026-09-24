# DSL Drafts

This page covers what a draft is, how the platform tracks drafts, and how to work with them
through the DSL Workbench, the starter REST API and dsl-builder.

Related: [Backend architecture](../architecture-backend.md) ·
[Runtime Engine — route table](../dsl/runtime.md#route-table) ·
[Configuration reference](../dsl/configuration.md) ·
[Runbook — approval gate](../runbook.md#publish-via-the-approval-gate)

---

## 1. What a draft is

A **draft** is an uncommitted change to a **DSL source file** in the server-side **DSL
workspace**. A DSL source file is a compact Java file written with the declarative DSL, such as
`LoanDisbursementDsl.java`. The workspace is a git working tree, so a draft has exactly the
status git gives the file relative to `HEAD`:

| Git state | Meaning | Draft status |
|-----------|---------|--------------|
| added | new DSL file that `HEAD` does not have, staged | `ADDED` |
| untracked | new DSL file that is not yet staged | `ADDED` |
| modified | `HEAD` has the file and the working tree has different content | `MODIFIED` |
| deleted | `HEAD` has the file and the working tree does not | `DELETED` |
| conflicting | unmerged path after a pull or rebase | `CONFLICTING` |
| clean | working tree equals `HEAD` | `PUBLISHED`, not a draft |

End users never touch files directly. They edit, compile, publish and discard drafts in the admin
UI's **DSL Workbench**. The Workbench calls the Spring Boot **starter**, which delegates all file
and git work to the **dsl-builder** service that owns the workspace.

### Terminology

| Term | Meaning |
|------|---------|
| DSL file | A `*.java` source under the workspace's DSL source root. The DSL annotation processor compiles it into Temporal workflows and activities. |
| Definition | A named DSL construct (process, transaction or function) declared in a DSL file. The definition `name` is the Workbench key. |
| Workspace | Directory on the server or in a Docker volume where dsl-builder keeps DSL sources: `cbs.dsl.builder.workspace-dir`, or `cbs.dsl.builder.source-dir` when that is set. |
| Worktree | The git working tree that holds the workspace. It is either a clone of `cbs.dsl.builder.git.repo-url` / `branch` / `sub-path`, or a pre-provisioned repo at `cbs.dsl.builder.git.repository-dir`. |
| Draft | A DSL file whose git status is not clean. |
| Published | The DSL file content at `HEAD`, which is also what the runtime has loaded. |
| Working set | All definitions with their status (`GET /api/dsl/working-set`). It drives the Workbench tree badges. |
| Pending write | A file write that dsl-builder accepted (`202`) and buffered, but has not yet flushed to disk. |
| Workbench record | JSON metadata for a definition under `.workbench/`. See [§6](#6-workbench-records-workbench). |

---

## 2. Architecture

```
 Browser ──► Nuxt BFF (/api/v1/dsl/*) ──► starter (Spring Boot, :8090)
                                              │   DslDraftHandler · DslFileHandler · DslReloadHandler
                                              │   DslBuilderClient (Resilience4j queue + bulkhead + breaker, BuilderCache)
                                              ▼
                                        dsl-builder (:8091)
                                              │   FileService · DraftService · GitStatusService
                                              │   WorkspaceGitService · CompileService
                                              ▼
                              workspace dir == git worktree (host folder / docker volume)
                                              │   optional push (push-on-commit)
                                              ▼
                                        remote repo (git.remote, default origin)
```

Rules:

1. **dsl-builder is the only writer of the workspace.** Every starter handler unconditionally delegates file,
   draft and git work to dsl-builder via the `DslBuilderClient` bean (always registered, no opt-out).
2. **Git is the source of truth for draft status.** Status is always derived from `git status`
   and never stored.
3. **Git model shared by both services.** `ChangeType`, `RepoStatus` and `GitChangeClassifier`
   live in `dsl-api`, package `cbs.nova.dsl.vcs`. Both services use them, and neither side
   needs JGit to interpret a status.

---

## 3. Status model

### 3.1 Classification

`GitChangeClassifier.classify(...)` maps JGit's `Status` sets to one `ChangeType` per
worktree-relative path:

| JGit `Status` set | Porcelain | `ChangeType` |
|-------------------|-----------|--------------|
| `getChanged()` (staged modify) | `M ` | `MODIFIED` |
| `getModified()` (unstaged modify) | ` M` | `MODIFIED` |
| `getUntracked()` | `??` | `UNTRACKED` |
| `getAdded()` | `A ` | `ADDED` |
| `getRemoved()` (staged delete) | `D ` | `DELETED` |
| `getMissing()` (unstaged delete) | ` D` | `DELETED` |
| `getConflicting()` | `UU` etc. | `CONFLICTING` |

A path can appear in more than one set, for example staged as added and then edited again. The
precedence, highest first, is `CONFLICTING > DELETED > ADDED > UNTRACKED > MODIFIED`. Staged and
unstaged changes are not separate states for the user. Renames show up as
`DELETED(old) + ADDED(new)`.

### 3.2 Repository status

`GET /api/dsl/vcs/status` (dsl-builder) returns a `RepoStatus`:

```json
{
  "workTree": "/abs/path",
  "dirtyPaths": ["dsl/LoanDsl.java", "dsl/NewDsl.java"],
  "changes": { "dsl/LoanDsl.java": "MODIFIED", "dsl/NewDsl.java": "UNTRACKED" }
}
```

- `dirtyPaths` is the key set of `changes`. A payload without `changes` still parses, and
  `changes` is then empty.
- `RepoStatus.changeOf(path)` looks up a single path.
- The starter's `DslGitStatusResolver` delegates to `DslBuilderClient.vcsStatus()` via the builder client.

**Freshness.** A saved edit is visible as a draft immediately:

- `VcsController.status` flushes pending `FileService` writes before it scans.
- Status is cached per repo root for `git.status-cache-ttl-seconds` (default 5). Every flush and
  every `DraftService` write invalidates that cache (`GitStatusService.invalidate()`).
- On the starter side, `DslBuilderClient` invalidates the matching `BuilderCache` entries after
  every successful mutating call: draft, files, VCS status and pending count.

### 3.3 Definition status

A definition inherits the status of the DSL file that declares it.
`DslDefinitionStatusResolver.resolveAll(names)` produces
`DefinitionStatus { PUBLISHED, DRAFT, MODIFIED, ADDED, DELETED, CONFLICTING }` for the working
set and other introspection endpoints:

1. `DslSourcePathResolver.relativePath(name)` maps the definition to its source file. It uses
   `GlobalManager.findFilename`, then a recursive lookup under `cbs.dsl.source-dir`. The same
   resolver backs `GET/POST /api/dsl/files/by-name/{name}`.
2. The path is matched against `RepoStatus.changes()` by `DslGitStatusResolver.matchChange`. A
   change key `K` matches path `P` if `K == P`, `K` ends with `/P`, or `P` ends with `/K`. This
   tolerates a builder worktree root that differs from the starter's source dir. The mapping is
   `ADDED | UNTRACKED → ADDED`, `MODIFIED → MODIFIED`, `DELETED → DELETED` and
   `CONFLICTING → CONFLICTING`.
3. If the file has no git change but a Workbench draft record `.workbench/drafts/<safeName>.json`
   exists, the status is `DRAFT`.
4. Otherwise the status is `PUBLISHED`.

The admin UI renders these values as chips (`ConstructStatus` in
`frontend/components/src/types/dsl.ts`, `PlainConstructList.vue`).

---

## 4. Lifecycle

```
            edit (write file)                   publish (reload + commit [+ push])
 PUBLISHED ───────────────────► MODIFIED ─────────────────────────────────► PUBLISHED'
     │                             │  ▲
     │ delete file                 │  │ edit / restore(commit)
     ▼                             ▼  │
  DELETED ──publish──► (gone)   discard (checkout HEAD) ──► PUBLISHED

 (none) ──create file──► ADDED ──publish──► PUBLISHED
                            └──discard (remove file)──► (none)
```

| Operation | Workspace / git effect | Runtime effect |
|-----------|------------------------|----------------|
| **Edit** | Writes the working-tree file. The write is buffered (`202`), then flushed. | none |
| **Compile / reload** | none | `POST /api/dsl/reload` compiles the sources through dsl-builder (`/api/dsl/compile`). Compile errors come back as diagnostics, and nothing is loaded. |
| **Publish** | Reloads first. On success, commits the definition's source path if it has a git change: `Publish <name>`, author = audit actor, email `<actor>@cbs-nova.local`. With `cbs.dsl.builder.git.push-on-commit=true` the commit is then pushed to `git.remote`. | Definitions are reloaded. If the reload fails, nothing is committed and the draft stays. |
| **Discard** | `MODIFIED` / `DELETED` → checkout `HEAD`. `ADDED` → unstage and delete. `UNTRACKED` → delete. Also drops the definition's Workbench draft record. | none |
| **History** | `git log -- <source path>`, newest first | none |
| **Restore(commit)** | Writes the file content at that commit into the working tree (stage + flush). The result is a `MODIFIED` draft. | none until publish |

### Invariants

- **I1.** Draft status is derived from `git status` and is never stored.
- **I2.** A commit is atomic. Either every listed path is committed or none is: the index is
  reset on failure. Only the listed paths are committed, and unrelated staged files stay staged.
- **I3.** Only files under the DSL source root are drafts. The workspace repo's `.gitignore` must
  exclude `.workbench/`, `build/` and `.gradle/`.
- **I4.** Paths are workspace-relative with forward slashes. `\` is normalised to `/`, a leading
  `/` is stripped, and anything containing `..` is rejected.
- **I5.** Pending writes are flushed before any status scan, commit or discard.
- **I6.** A push never forces. A failed push keeps the local commit and is reported as
  `pushed=false` with a `pushError`.
- **I7.** The publish approval gate (`cbs.dsl.approval.required`, T568) applies to every publish.
  A change request snapshots the draft, and approval replays the same publish flow.

---

## 5. API

The BFF exposes all starter routes under `/api/v1/dsl/...`, as explicit Nitro routes in
`frontend/admin-ui-plugin/server/api/v1/dsl/`. There is no catch-all.

### 5.1 Starter: drafts

| Method | Path | Handler | Behaviour |
|--------|------|---------|-----------|
| GET | `/api/dsl/drafts?limit&offset` | `DslDraftHandler.list` | `PageResponse<DraftSummary>` of Workbench draft records |
| GET | `/api/dsl/drafts/{name}` | `read` | `DraftRequest`, or 404 |
| POST | `/api/dsl/drafts/{name}/save` | `save` | Stores the Workbench draft record (body `DraftRequest`, `name` required). Audit `DRAFT_WRITE`, event `DraftSaved`. |
| POST | `/api/dsl/drafts/{name}/publish` | `publish` | Publish flow (§4, §5.5). 403 when the approval gate is on and the caller is below OPERATOR. |
| POST | `/api/dsl/drafts/{name}/change-request` | `ChangeRequestHandler.submit` | Submits the draft for approval |
| POST | `/api/dsl/drafts/{name}/discard` | `discard` | Reverts the source file to `HEAD` and drops the draft record (a 404 there is ignored). Returns `status: "Discarded"`. Audit `DRAFT_DISCARD`. 404 when the definition has no source path. |
| DELETE | `/api/dsl/drafts/{name}` | `delete` | Deletes the Workbench draft record only; the source file is untouched |
| GET | `/api/dsl/drafts/{name}/commits?limit` | `commits` | `git log` of the definition's source path |
| GET | `/api/dsl/drafts/{name}/history` | `history` | `DefinitionHistoryEntry[]`, newest first. For git history, `timestamp` is a commit id, `timestampMillis`/`lastModifiedMillis` are the commit time and `sizeBytes` is `-1`. |
| GET | `/api/dsl/drafts/{name}/history/{ts}` | `historyEntry` | `DraftRequest` whose `source` is the file at that commit, with status `History`. Metadata comes from the draft record when there is one. 404 if the file is missing at that commit or the commit is unknown. |
| GET | `/api/dsl/drafts/{name}/history/{ts}/diff` | `historyDiff` | `HistoryDiffResponse`: the file at that commit (`before`) against the current working tree (`after`), as `LineDiff` hunks |
| POST | `/api/dsl/drafts/{name}/history/{ts}/restore` | `restore` | Stages the commit's content as a `MODIFIED` draft, with no reload. Returns `status: "Draft"`. Audit `DRAFT_RESTORE`. |
| GET | `/api/dsl/definitions/export[?include=drafts]` | `exportBundle` | `DefinitionBundle` |
| POST | `/api/dsl/definitions/import[?dryRun=true]` | `importBundle` | Digest-verified, at most 200 definitions, then a reload |

`{ts}` is a git commit id when it is 7–40 hex characters and is either 40 characters long or
contains a letter a–f. An all-digit `{ts}` addresses a Workbench history snapshot (§6). A
snapshot is also used when the definition has no source path or git is not configured.

`discard`, `commits` and git history all go through the dsl-builder. Without
`cbs.dsl.source-dir` the handlers return `409 NOT_CONFIGURED`. If the directory does not exist they
return `409 NOT_FOUND`.

### 5.2 Starter: files and status

| Method | Path | Behaviour |
|--------|------|-----------|
| GET | `/api/dsl/files?prefix` | List DSL files |
| GET | `/api/dsl/files/{*path}` | Read a file. Pending writes are served first. |
| GET | `/api/dsl/files/by-name/{name}` | Read the source file of a definition |
| POST | `/api/dsl/files/{*path}` | Stage a write (`202`). Body is raw text or `{"content": "..."}`. |
| POST | `/api/dsl/files/by-name/{name}` | Stage a write to a definition's source file |
| POST | `/api/dsl/files/bulk` | Stage several writes |
| POST | `/api/dsl/files/flush` | Flush pending writes to disk |
| GET | `/api/dsl/files/status` | `{ pending }` |
| GET | `/api/dsl/working-set` | Definitions with `DefinitionStatus` (§3.3) |
| POST | `/api/dsl/reload` | Compile and load definitions |

### 5.3 dsl-builder: version control

`VcsController`, backed by `WorkspaceGitService` (JGit):

| Method | Path | Body / params | Result |
|--------|------|---------------|--------|
| GET | `/api/dsl/vcs/status` | – | `RepoStatus`. 404 when there is no repo. |
| POST | `/api/dsl/vcs/commit` | `CommitRequest {paths, message, authorName, authorEmail}` | `CommitResult {commitId, paths, timestampMillis, pushed, pushError}`. `pushed` is `null` when push-on-commit is off. 409 `NOTHING_TO_COMMIT` if any listed path is clean. |
| POST | `/api/dsl/vcs/discard` | `DiscardRequest {paths}` | `DiscardResult {discarded}`. Clean paths are skipped. |
| GET | `/api/dsl/vcs/log?path&limit` | `limit` defaults to 20, clamped to 1..200 | `LogEntry[] {commitId, timestampMillis, author, message}`, newest first |
| GET | `/api/dsl/vcs/show?path&commit` | – | `{path, commitId, content}`, or 404 |

Commit and discard flush pending writes first. When the workspace is a subdirectory of the repo,
paths are translated to and from repo-relative form. A blank path or one containing `..` returns
400 `INVALID_PATH`. Git disabled or no repo returns 409 `GIT_NOT_CONFIGURED`.

dsl-builder also serves `DraftController` (`/api/dsl/drafts/**`, Workbench records),
`DefinitionBundleController`, `FileController` (`/api/dsl/files/**`) and `CompileController`
(`/api/dsl/compile`, `/api/dsl/compile/{id}/download`). Errors are
`BuilderApiException(status, code, message)`, which `DslBuilderClient` maps back. A full request
queue raises `BuilderClientBusyException`. An open breaker or unreachable host raises
`BuilderUnavailableException`.

### 5.4 BFF routes

Each starter route above has a Nitro proxy route under
`frontend/admin-ui-plugin/server/api/v1/dsl/`. The draft git routes are
`drafts/[name]/discard.post.ts` and `drafts/[name]/commits.get.ts`. History routes pass `{ts}`
through unchanged, so both commit ids and snapshot timestamps work.

### 5.5 Publish response and side effects

`DraftResponse { name, status, location, reloaded, loadResult, reloadError, diagnostics, savedAt, commitId }`

- **Reload fails:** the response is 200 with `reloaded=false`, `reloadError` and up to 20
  `diagnostics`. The diagnostics are persisted through `CompileDiagnosticRecordRepository`
  (source `PUBLISH`). Nothing is committed.
- **Reload succeeds and the source file has a git change:** the file is committed and
  `commitId` is set.
- **Commit fails:** the publish still returns 200, with `commitId: null`. The audit entry
  records `commitError`.
- **Audit:** `DEFINITION_PUBLISH`, with `commitId`, `message`, `pushed` and `pushError` when
  present.
- **Domain event:** `DraftPublished`, best-effort (a failure is logged and swallowed).
- **Rate limiting:** `POST .../save`, `.../publish` and `.../discard` and `DELETE /drafts/{name}`
  are rate-limited when `cbs.security.ratelimit.enabled=true`.

---

## 6. Workbench records (`.workbench/`)

Alongside the DSL files, the workspace keeps JSON metadata for the Workbench. It lives under
`.workbench/`, which git ignores, so it never shows up as a draft:

```
<workspace>/
  .workbench/drafts/<safeName>.json               draft record      (save; removed by publish/discard/delete)
  .workbench/published/<safeName>.json            published record  (publish, bundle import)
  .workbench/history/<safeName>/<epochMs>.json    snapshot taken before each publish
```

- `safeName = name.replaceAll("[^A-Za-z0-9._-]", "_")`.
- A record is a `DraftRequest { name, type, status, version, taskQueue, source, savedAt }`.
- Draft records carry Workbench metadata (type, version, task queue) and give definitions
  without a git change a `DRAFT` status (§3.3).
- Published records feed bundle export and import.
- History snapshots answer history requests with an all-digit `{ts}`. A snapshot restore
  re-publishes the snapshot and reloads.
- dsl-builder keeps up to `cbs.dsl.builder.drafts.history-limit` snapshots per definition
  (default 20). Directories are configured with `cbs.dsl.builder.workbench.*`.

---

## 7. Working with drafts

Edit a definition's source file; it becomes a draft immediately:

```bash
curl -X POST localhost:8090/api/dsl/files/by-name/LoanDisbursement \
     -H 'Content-Type: application/json' -d '{"content":"<java source>"}'   # 202, buffered
curl localhost:8090/api/dsl/working-set                                      # LoanDisbursement: Modified
```

Publish (reload, commit, and push if enabled):

```bash
curl -X POST localhost:8090/api/dsl/drafts/LoanDisbursement/publish \
     -H 'Content-Type: application/json' -d '{"name":"LoanDisbursement"}'
# → DraftResponse: check "reloaded", "diagnostics", "commitId"
```

Throw the change away:

```bash
curl -X POST localhost:8090/api/dsl/drafts/LoanDisbursement/discard
```

Browse history, compare and roll back into a draft:

```bash
curl localhost:8090/api/dsl/drafts/LoanDisbursement/history
curl localhost:8090/api/dsl/drafts/LoanDisbursement/history/<commitId>/diff
curl -X POST localhost:8090/api/dsl/drafts/LoanDisbursement/history/<commitId>/restore
# → a MODIFIED draft; publish it to make it live
```

---

## 8. Deployment

- Mount the DSL repo, or an empty volume plus `cbs.dsl.builder.git.repo-url`, at
  `cbs.dsl.builder.workspace-dir`. Only dsl-builder mounts it read-write.
- Keep `cbs.dsl.builder.git.enabled=true` (the default). Without a git repo,
  `/api/dsl/vcs/status` returns 404, every definition resolves to `PUBLISHED` or `DRAFT`, and the
  git operations return `GIT_NOT_CONFIGURED`.
- Add `.workbench/`, `build/` and `.gradle/` to the workspace repo's `.gitignore` (I3).
- To publish to a shared repo, set `cbs.dsl.builder.git.push-on-commit=true` and make sure the
  `git.remote` is reachable with the credentials dsl-builder runs under.

---

## 9. Configuration

All keys use the `cbs.dsl.` prefix.

| Key | Default | Service | Purpose |
|-----|---------|---------|---------|
| `cbs.dsl.drafts.enabled` | `true` | starter | Registers `DslDraftHandler` and its routes |
| `cbs.dsl.source-dir` | – | starter | DSL source dir. Required by the draft handlers. |
| `cbs.dsl.approval.required` | `false` | starter | Publish approval gate |
| `cbs.dsl.builder-client.base-url` | `http://localhost:8091` | starter | Delegate to dsl-builder |
| `cbs.dsl.builder.workspace-dir` / `source-dir` | – | dsl-builder | Workspace root (`source-dir` wins for the file API) |
| `cbs.dsl.builder.git.{enabled,repository-dir,worktrees-dir,repo-url,sub-path,branch}` | `true`, … | dsl-builder | Worktree provisioning |
| `cbs.dsl.builder.git.status-cache-ttl-seconds` | `5` | dsl-builder | Status cache TTL |
| `cbs.dsl.builder.git.push-on-commit` / `remote` | `false` / `origin` | dsl-builder | Push after each publish commit |
| `cbs.dsl.builder.files.{flush-interval-seconds,max-queue-size,read-bulkhead-permits,write-bulkhead-permits}` | `5`, `100`, `32`, `8` | dsl-builder | Write buffer |
| `cbs.dsl.builder.drafts.history-limit` | `20` | dsl-builder | Snapshots kept per definition |
| `cbs.dsl.builder.workbench.*` | see `DslBuilderProperties.Workbench` | dsl-builder | Record dirs, bundle limits, diff hunks/context |

---

## 10. Testing

| Area | Tests |
|------|-------|
| Change classification, `RepoStatus` | `dsl-api` `vcs/GitChangeClassifierTest` |
| Builder git status + freshness | `GitStatusServiceTest`, `controller/VcsControllerTest` |
| Builder commit / discard / log / show / push | `WorkspaceGitServiceTest`, `controller/VcsControllerTest` |
| Builder files and flush | `FileServiceTest`, `FileBufferTest`, `FileBulkheadTest`, `controller/FileControllerTest` |
| Builder Workbench records | `DraftServiceTest`, `controller/DraftControllerTest` |
| Starter git status | `service/DslGitStatusResolverTest` |
| Definition → source path, definition status | `service/DslSourcePathResolverTest`, `service/DslDefinitionStatusResolverTest` |
| Publish commit, discard, commits, git history / diff / restore | `DslDraftGitResourceTest` |
| Local-mode draft routes | `DslDraftResourceTest` |
| Approval gate | `DslDraftApprovalGateTest`, `approval/ChangeRequestServiceTest` |
| Bundles | `DslDefinitionBundleResourceTest` |
| Builder client contract + cache invalidation | `builder/DslBuilderClientTest`, `builder/BuilderCacheTest` |
| Property binding | `config/CbsDslPropertyPrefixTest`, `config/properties/DslBuilderClientPropertiesTest` |
| Rate limiting | `web/RateLimitFilterTest` |
| Working set | `DslIntrospectionServiceTest`, `DslIntrospectionResourceTest` |
| BFF | `server/api/v1/dsl/drafts/__tests__/drafts.spec.ts`, `drafts-git.spec.ts` |
| UI status chips | `components/src/components/__tests__/PlainConstructList.spec.ts` |

Git behaviour is tested against real JGit repositories in `@TempDir`, not mocks. Push is tested
against a local bare repository.

```bash
backend/dsl-platform/gradlew -p backend/dsl-platform :dsl-api:test --tests '*vcs*'
backend/dsl-platform/gradlew -p backend/dsl-plugins :dsl-builder:test
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test \
  --tests '*Draft*' --tests '*GitStatus*' --tests '*StatusResolver*' --tests '*SourcePath*' \
  --tests '*BuilderClient*' --tests '*BuilderCache*' --tests '*Prefix*' --tests '*Introspection*'
cd frontend/admin-ui-plugin && npx vitest run server/api/v1/dsl/drafts
```

---

## 11. Code map

| Concern | Starter (`backend/dsl-starter/starter/.../cbs/nova/starter`) | dsl-builder (`backend/dsl-plugins/dsl-builder/.../cbs/nova/dsl/builder`) |
|---------|------|------|
| Shared git model | `dsl-api` `cbs.nova.dsl.vcs` (`ChangeType`, `RepoStatus`, `GitChangeClassifier`) | same |
| Draft HTTP | `controller/DslDraftHandler`, `config/router/DslDraftRouterConfiguration` | `controller/DraftController`, `DefinitionBundleController` |
| Git status | `service/DslGitStatusResolver` | `service/GitStatusService`, `controller/VcsController` |
| Git operations | `builder/DslBuilderClient.commit/discard/log/show` | `service/WorkspaceGitService` |
| Definition → source path | `service/DslSourcePathResolver` | – |
| Definition status | `service/DslDefinitionStatusResolver`, `model/DslIntrospectionModels.DefinitionStatus` | – |
| Files | `controller/DslFileHandler`, `service/DslFileService` | `controller/FileController`, `service/FileService`, `FileBuffer`, `FileBulkhead` |
| Workbench records | `service/DslDefinitionHistoryService`, `DslDefinitionBundleService` | `service/DraftService`, `DefinitionHistoryService`, `DefinitionBundleService` |
| Compile / reload | `controller/DslReloadHandler` | `controller/CompileController`, `service/CompileService`, `GradleService` |
| Clone / worktree | – | `service/GitService`, `RepoUrlValidator` |
| Remote client | `builder/DslBuilderClient`, `BuilderCache`, `config/BuilderClientConfiguration` | – |
