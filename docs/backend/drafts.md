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
| Workspace | Directory that backs DSL file I/O. Resolved by dsl-builder from `cbs.dsl.builder.workspace-mode` (§1.1). |
| Workspace mode | How the builder picks the workspace: `SHARED` (the shared source directory, dev/local default) or `WORKTREE` (a builder-managed git worktree). |
| Worktree | The git working tree that holds the workspace. In `SHARED` mode it is the shared source directory itself; in `WORKTREE` mode it is a clone of `cbs.dsl.builder.git.repo-url` / `branch` / `sub-path` provisioned at `cbs.dsl.builder.workspace-dir`. |
| Draft | A DSL file whose git status is not clean. |
| Published | The DSL file content at `HEAD`, which is also what the runtime has loaded. |
| Working set | All definitions with their status (`GET /api/dsl/working-set`). It drives the Workbench tree badges. |
| Pending write | A file write that dsl-builder accepted (`202`) and buffered, but has not yet flushed to disk. |

### 1.1 Workspace modes

The builder is the source of truth for the workspace, selected by
`cbs.dsl.builder.workspace-mode` (default `SHARED`):

- **`SHARED`** — the builder works directly on `cbs.dsl.builder.source-dir` (falling back to
  `cbs.dsl.builder.workspace-dir`). File reads/writes hit exactly the files the user edits in the
  IDE; git operations run in that directory. Deploy it so the builder's source dir equals the
  starter's `cbs.dsl.source-dir`. If the directory is not inside a git repository, VCS endpoints
  return `GIT_NOT_CONFIGURED` and every definition resolves to `PUBLISHED`. This is the
  dev/local mode.
- **`WORKTREE`** — the builder manages `cbs.dsl.builder.workspace-dir` as its own git worktree,
  cloned from `cbs.dsl.builder.git.repo-url` / `branch` / `sub-path` when the workspace is empty
  and no repository is found there. The starter delegates everything there; draft metadata
  reports the builder's actual workspace path so the user knows where the file lives.

`GET /api/dsl/workspace` (builder) returns `{mode, workTree}` — the live resolution of the mode
and the absolute directory backing file I/O. The starter's `GET /api/dsl/drafts/metadata`
consumes it: `sourcePath` is the builder-reported `workTree` when the builder is reachable
(the starter's `cbs.dsl.source-dir` is only a fallback), and `workspaceMode` surfaces the mode.
---

## 2. Architecture

```
 Browser ──► Nuxt BFF (/api/v1/dsl/*) ──► starter (Spring Boot, :8090)
                                              │   DslDraftHandler · DslFileHandler · DslReloadHandler
                                              │   DslBuilderClient (Resilience4j queue + bulkhead + breaker, BuilderCache)
                                              ▼
                                        dsl-builder (:8091)
                                              │   FileService · GitStatusService
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
  "changes": { "dsl/LoanDsl.java": "MODIFIED", "dsl/NewDsl.java": "UNTRACKED" },
  "sizes": { "dsl/LoanDsl.java": 4211, "dsl/NewDsl.java": 98 }
}
```

- `dirtyPaths` is the key set of `changes`. A payload without `changes` still parses, and
  `changes` is then empty.
- `sizes` maps each changed path to its on-disk size in bytes, read right after the JGit scan.
  Deleted paths are omitted. A payload without `sizes` still parses, and `sizes` is then empty.
- `RepoStatus.changeOf(path)` looks up a single path; `RepoStatus.sizeOf(path)` a single size.
- The starter's `DslGitStatusResolver` delegates to `DslBuilderClient.vcsStatus()` via the builder client.

**Freshness.** A saved edit is visible as a draft immediately:

- `VcsController.status` flushes pending `FileService` writes before it scans.
- Status is cached per repo root for `git.status-cache-ttl-seconds` (default 5). Every flush, commit
  and discard invalidates that cache (`GitStatusService.invalidate()`).
- On the starter side, `DslBuilderClient` invalidates the matching `BuilderCache` entries after
  every successful mutating call: draft, files, VCS status and pending count.

### 3.3 Definition status

A definition inherits the status of the DSL file that declares it.
`DslDefinitionStatusResolver.resolveAll(names)` produces
`DefinitionStatus { PUBLISHED, MODIFIED, ADDED, DELETED, CONFLICTING }` for the working
set and other introspection endpoints:

1. `DslSourcePathResolver.relativePath(name)` maps the definition to its source file. It uses
   `GlobalManager.findFilename`, then a recursive lookup under `cbs.dsl.source-dir`. The same
   resolver backs `GET/POST /api/dsl/files/by-name/{name}`.
2. The path is matched against `RepoStatus.changes()` by `DslGitStatusResolver.matchChange`. A
   change key `K` matches path `P` if `K == P`, `K` ends with `/P`, or `P` ends with `/K`. This
   tolerates a builder worktree root that differs from the starter's source dir. The mapping is
   `ADDED | UNTRACKED → ADDED`, `MODIFIED → MODIFIED`, `DELETED → DELETED` and
   `CONFLICTING → CONFLICTING`.
3. A file with no git change is `PUBLISHED`.

The admin UI renders these values as status icons (`ConstructStatusIcon.vue`) next to a
status-colored name (`ConstructStatus` in `frontend/components/src/types/dsl.ts`,
`PlainConstructList.vue`). Build/compile statuses (`Draft`, `Valid`, `Invalid`) support optional
strikethrough and bold emphasis on the name when the status signals a build problem.

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
| **Discard** | `MODIFIED` / `DELETED` → checkout `HEAD`. `ADDED` → unstage and delete. `UNTRACKED` → delete. | none |
| **History** | `git log -- <source path>`, newest first | none |
| **Restore(commit)** | Writes the file content at that commit into the working tree (stage + flush). The result is a `MODIFIED` draft. | none until publish |

### 4.1 One flow: draft → change request → approval → publish

With `cbs.dsl.approval.required=true` a draft is not published directly. Draft, branch, change
request and approval form one flow:

```
 edit ──► DRAFT (MODIFIED/ADDED/DELETED on workspace branch)
            │ POST /drafts/{name}/change-request   (snapshot of source file)
            ▼
        CR PENDING ──new CR for same definition──► older CR SUPERSEDED
            │ approve (AUTHOR+, not requester unless ADMIN)      │ reject (+ comment)
            ▼                                                     ▼
        CR APPROVED ──► publishPayload: reload → commit [+ push]  CR REJECTED (draft stays)
                            │
                            ▼
                   PUBLISHED (commit on workspace branch = `commitId`)
```

| Step | Draft side | Change-request side |
|------|------------|---------------------|
| Edit / save | working-tree file changes, status `MODIFIED`/`ADDED`/`DELETED` | none |
| Submit | file is read as-is, not modified | row in `dsl_change_request`, status `PENDING`, `draftContent` = `DraftRequest` JSON snapshot. Any earlier `PENDING` CR of the definition becomes `SUPERSEDED`. Audit `CHANGE_REQUEST_CREATE`. |
| Approve | snapshot `source` is written back and published (§5.5). Response is the normal `DraftResponse` (`reloaded`, `commitId`, ...). | status `APPROVED`, `approvedBy`, `approvedAt`, `comment`. Audit `CHANGE_REQUEST_APPROVE`. |
| Reject | working tree untouched, draft stays | status `REJECTED`, `comment`. Never publishes. Audit `CHANGE_REQUEST_REJECT`. |
| Discard draft | file reverts to `HEAD` | an open `PENDING` CR is not touched; it still holds its snapshot |

**Branch.** The workspace has exactly one checked-out branch (`GET /api/dsl/vcs/branch`,
`DraftsMetadata.gitBranch`). All drafts, all snapshots and all approval commits land on that
branch. A change request stores no branch of its own. The commit created by an approved publish
is the link between a CR and the branch history: `commitId` in the publish response, then
`GET /api/dsl/drafts/{name}/commits` / `history`. With `git.push-on-commit=true` the approved
commit is pushed to `git.remote` on that same branch.

**Rules.**

- Only `PENDING` CRs can be decided. Anything else returns `409`.
- Deciding needs role `AUTHOR` or higher. The requester cannot approve or reject their own CR
  unless `ADMIN`. Both violations return `403 FORBIDDEN`.
- The snapshot is what gets published, not the current file. Edits after submit are not part of
  the CR. Submit again to replace the CR.
- Publish failure on approve (reload error) does not roll back the CR status: the CR is already
  `APPROVED`, and the response carries `reloaded=false` and `diagnostics`. Fix the source, submit a
  new CR.
- No `DataSource` → no `ChangeRequestService` bean → no change-request routes.

### Invariants

- **I1.** Draft status is derived from `git status` and is never stored.
- **I2.** A commit is atomic. Either every listed path is committed or none is: the index is
  reset on failure. Only the listed paths are committed, and unrelated staged files stay staged.
- **I3.** Only files under the DSL source root are drafts. The workspace repo's `.gitignore` must
  exclude `build/` and `.gradle/`.
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
| GET | `/api/dsl/drafts?limit&offset` | `DslDraftHandler.list` | `PageResponse<DraftSummary>` of definitions whose source file has a git change, sorted by name |
| GET | `/api/dsl/drafts/{name}` | `read` | `DraftDetail { name, type, status }` with the definition's name, type and current `DefinitionStatus`. Status only — no source content; read the working-tree source via `GET /api/dsl/files/by-name/{name}`. 404 when the definition has no source path. |
| POST | `/api/dsl/drafts/{name}/save` | `save` | Writes the body's `source` to the definition's source file (stage + flush). 400 when `source` is blank, 404 when there is no source path. Returns `status: "Draft"`. Audit `DRAFT_WRITE`, event `DraftSaved`. |
| POST | `/api/dsl/drafts/{name}/publish` | `publish` | Writes the body's `source` first when it is present, then runs the publish flow (§4, §5.5). 403 when the approval gate is on and the caller is below OPERATOR. |
| POST | `/api/dsl/drafts/{name}/change-request` | `ChangeRequestHandler.submit` | Submits the draft for approval |
| POST | `/api/dsl/drafts/{name}/discard` | `discard` | Reverts the source file to `HEAD`. Returns `status: "Discarded"`. Audit `DRAFT_DISCARD`. 404 when the definition has no source path. |
| DELETE | `/api/dsl/drafts/{name}` | `delete` | Same as `discard` |
| GET | `/api/dsl/drafts/{name}/commits?limit` | `commits` | `git log` of the definition's source path |
| GET | `/api/dsl/drafts/metadata` | `metadata` | `DraftsMetadata {draftCount, sourcePath, sizeMb, gitBranch, gitEnabled, statusCacheTtlSeconds, workspaceMode}`: the number and size of changed DSL files, the actual workspace directory (builder-reported `workTree` from `GET /api/dsl/workspace`, falling back to `cbs.dsl.source-dir`), the workspace mode (`SHARED`/`WORKTREE`, `null` when the builder is unreachable), the workspace branch (from dsl-builder `GET /api/dsl/vcs/branch`) and the git settings. `sizeMb` is the total of the builder-reported `RepoStatus.sizes` for changed files under the source root, rounded to two decimals (`0.0` when there are no drafts, `null` when the status carries no sizes). Shown in the Workbench `DraftMetadataCard`. |
| GET | `/api/dsl/drafts/{name}/history` | `history` | `DefinitionHistoryEntry[]` from `git log` of the source file, newest first, at most `DslDraftHandler.HISTORY_LIMIT` (50). `timestamp` is the commit id, `timestampMillis`/`lastModifiedMillis` are the commit time and `sizeBytes` is `-1`. |
| GET | `/api/dsl/drafts/{name}/history/{ts}` | `historyEntry` | `DraftRequest` whose `source` is the file at commit `{ts}`, with status `History`. 404 if the file is missing at that commit or the commit is unknown. |
| GET | `/api/dsl/drafts/{name}/history/{ts}/diff` | `historyDiff` | `HistoryDiffResponse`: the file at that commit (`before`) against the current working tree (`after`), as `LineDiff` hunks |
| POST | `/api/dsl/drafts/{name}/history/{ts}/restore` | `restore` | Stages the commit's content as a `MODIFIED` draft, with no reload. Returns `status: "Draft"`. Audit `DRAFT_RESTORE`. |
| GET | `/api/dsl/definitions/export[?include=drafts]` | `exportBundle` | `DefinitionBundle` of every definition's source file: content at `HEAD`, or the working tree with `include=drafts` |
| POST | `/api/dsl/definitions/import[?dryRun=true]` | `importBundle` | Digest-verified, at most 200 definitions. `dryRun` compares each entry with the current file. Otherwise it writes each entry to its definition's source file (unknown names are `skipped`), reloads, and commits all written files as one `Import bundle` commit. |
| POST | `/api/dsl/drafts/fetch` | `DslDraftVcsHandler.fetch` | Proxies to builder `POST /api/dsl/vcs/fetch`: fetches `origin` and reports `FetchResult {branch, fetched, remoteHead?, ahead, behind}`. Flushes pending writes first. 409 when `cbs.dsl.source-dir` is unset/missing or the git remote/repo is absent; 409 `GIT_REQUIRES_BUILDER` when the builder is unreachable. Audit `DRAFT_VCS_FETCH`, event `DraftsRepoFetched`. |
| POST | `/api/dsl/drafts/pull` | `pull` | Proxies to builder `POST /api/dsl/vcs/pull` (§5.3). Fast-forwards when strictly behind, rebases (default) or merges per `cbs.dsl.builder.git.pull-strategy` when diverged; a conflict never destroys local commits (§5.3). Audit `DRAFT_VCS_PULL`, event `DraftsRepoPulled`. |
| POST | `/api/dsl/drafts/rebase` | `rebase` | Proxies to builder `POST /api/dsl/vcs/rebase`: fetch first, rebase the branch onto its upstream; fast-forwards when strictly behind; a conflict aborts the rebase and leaves the working tree untouched. Audit `DRAFT_VCS_REBASE`, event `DraftsRepoRebased`. |
| POST | `/api/dsl/drafts/commit` | `commit` | Batch commit. Body `BatchCommitRequest {message, names}`. Every listed definition name is resolved to its source path first — 404 when any name is unknown, before anything is touched — then pending writes are flushed and all paths are committed as one atomic commit (§5.3). 400 on blank `message` or empty `names`. Audit `DRAFTS_COMMIT`, event `DraftsCommitted`. |

`{ts}` is always a git commit id.

`discard`, `commits` and git history all go through the dsl-builder. Without
`cbs.dsl.source-dir` the handlers return `409 NOT_CONFIGURED`. If the directory does not exist they
return `409 NOT_FOUND`.

### 5.1.1 Starter: change requests

`ChangeRequestHandler` (routes in `config/router/ChangeRequestRouterConfiguration`), backed by
`ChangeRequestService`. See §4.1 for the flow.

| Method | Path | Handler | Behaviour |
|--------|------|---------|-----------|
| POST | `/api/dsl/drafts/{name}/change-request` | `submit` | Snapshots the definition's source file. `201` + `ChangeRequestEntity`. 404 when no source path or file, 409 when `cbs.dsl.source-dir` is unset. |
| GET | `/api/dsl/change-requests?definitionName&status` | `list` | Newest first. `status` is `PENDING`, `APPROVED`, `REJECTED` or `SUPERSEDED` (case-insensitive), otherwise 400 `INVALID_REQUEST`. |
| POST | `/api/dsl/change-requests/{id}/approve` | `approve` | Optional body `{comment}`. Publishes the snapshot, returns the publish result (§5.5). 403 / 404 / 409. |
| POST | `/api/dsl/change-requests/{id}/reject` | `reject` | Optional body `{comment}`. Returns the rejected `ChangeRequestEntity`. 403 / 404 / 409. |

`ChangeRequestEntity` (`dsl_change_request`): `id`, `definitionName`, `draftContent` (snapshot
JSON), `requestedBy`, `requestedAt`, `status`, `approvedBy`, `approvedAt`, `comment`.

Admin UI: the **Approvals** page (`frontend/admin-ui-plugin/app/pages/approvals.vue`,
`useApprovals`) lists CRs with a status filter, pending first. Approve and Reject buttons are
guarded by the manifest guard `workbench-approve`, and Reject requires a reason. The backend
re-checks role and guard.

### 5.2 Starter: files and status

| Method | Path | Behaviour |
|--------|------|-----------|
| GET | `/api/dsl/files?prefix` | List DSL files |
| GET | `/api/dsl/files/{*path}` | Read a file: `FileContentResponse { path, content, crc32 }`. Pending writes are served first. |
| GET | `/api/dsl/files/by-name/{name}` | Read the source file of a definition: `FileContentResponse { path, content, crc32 }` |
| POST | `/api/dsl/files/{*path}` | Stage a write (`202`). Body is raw text or `{"content": "..."}`. |
| POST | `/api/dsl/files/by-name/{name}` | Stage a write to a definition's source file |
| POST | `/api/dsl/files/bulk` | Stage several writes |
| POST | `/api/dsl/files/flush` | Flush pending writes to disk |
| GET | `/api/dsl/files/status` | `{ pending }` |
| GET | `/api/dsl/working-set` | Definitions with `DefinitionStatus` (§3.3) |
| POST | `/api/dsl/reload` | Compile and load definitions |

### 5.3 dsl-builder: version control

`VcsController`, backed by `WorkspaceGitService` (JGit). All git operations run in the
builder's workspace work tree, resolved per `cbs.dsl.builder.workspace-mode` (§1.1): the shared
source directory in `SHARED` mode, `cbs.dsl.builder.workspace-dir` in `WORKTREE` mode.

| Method | Path | Body / params | Result |
|--------|------|---------------|--------|
| GET | `/api/dsl/vcs/status` | – | `RepoStatus`. 404 when there is no repo. |
| GET | `/api/dsl/workspace` | – | `{mode, workTree}` — workspace mode (`SHARED`/`WORKTREE`) and the absolute directory backing file I/O. Consumed by the starter draft metadata. |
| GET | `/api/dsl/vcs/branch` | – | `{branch}` of the workspace repo |
| POST | `/api/dsl/vcs/commit` | `CommitRequest {paths, message, authorName, authorEmail}` | `CommitResult {commitId, paths, timestampMillis, pushed, pushError}`. `pushed` is `null` when push-on-commit is off. 409 `NOTHING_TO_COMMIT` if any listed path is clean. |
| POST | `/api/dsl/vcs/fetch` | – | `FetchResult {branch, fetched, remoteHead?, ahead, behind}`: fetches `origin` and reports the branch's ahead/behind against its upstream. 409 `VCS_ERROR` when the branch has no upstream. |
| POST | `/api/dsl/vcs/pull` | – | `PullResult {branch, strategy, pulled, updatedFrom?, updatedTo?, ahead, behind, message?}`. No-op when up to date; fast-forward only (never a merge) when strictly behind; when diverged, rebase onto upstream (default) or merge per `cbs.dsl.builder.git.pull-strategy`. A rebase conflict aborts the rebase so local commits and the tree survive (`message` explains); a merge conflict leaves the repo `MERGING` for the caller to resolve. |
| POST | `/api/dsl/vcs/rebase` | – | `RebaseResult {branch, rebased, updatedFrom?, updatedTo?, ahead, behind, message?}`. Fetch first, then rebase onto upstream; fast-forwards when strictly behind; no-op when up to date; a conflict aborts the rebase, leaving the working tree untouched. |
| POST | `/api/dsl/vcs/discard` | `DiscardRequest {paths}` | `DiscardResult {discarded}`. Clean paths are skipped. |
| GET | `/api/dsl/vcs/log?path&limit` | `limit` defaults to 20, clamped to 1..200 | `LogEntry[] {commitId, timestampMillis, author, message}`, newest first |
| GET | `/api/dsl/vcs/show?path&commit` | – | `{path, commitId, content}`, or 404 |

Commit, discard, fetch, pull and rebase flush pending writes first. When the workspace is a subdirectory of the repo,
paths are translated to and from repo-relative form. A blank path or one containing `..` returns
400 `INVALID_PATH`. Git disabled or no repo returns 409 `GIT_NOT_CONFIGURED`. None of fetch, pull or
rebase ever pushes; pull and rebase never force and never drop local commits — a conflict either aborts
the rebase cleanly or leaves the repo `MERGING`.

dsl-builder also serves `FileController` (`/api/dsl/files/**`) and `CompileController`
(`/api/dsl/compile`, `/api/dsl/compile/{id}/download`). Errors are
`BuilderApiException(status, code, message)`, which `DslBuilderClient` maps back. A full request
queue raises `BuilderClientBusyException`. An open breaker or unreachable host raises
`BuilderUnavailableException`.

### 5.4 BFF routes

Each starter route above has a Nitro proxy route under
`frontend/admin-ui-plugin/server/api/v1/dsl/`. The draft git routes are
`drafts/[name]/discard.post.ts` and `drafts/[name]/commits.get.ts`. History routes pass `{ts}`
through unchanged. The metadata route is `drafts/metadata.get.ts`.

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

## 6. Working with drafts

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

## 7. Deployment

- Mount the DSL repo, or an empty volume plus `cbs.dsl.builder.git.repo-url`, at
  `cbs.dsl.builder.workspace-dir`. Only dsl-builder mounts it read-write.
- Local/dev deployments: keep the default `cbs.dsl.builder.workspace-mode=SHARED` and point
  `cbs.dsl.builder.source-dir` (or `workspace-dir`) at the same directory as the starter's
  `cbs.dsl.source-dir`, so the API edits exactly the files the IDE shows. `WORKTREE` mode is for
  deployments where the builder owns a cloned worktree (e.g. the Docker Compose service).
- Keep `cbs.dsl.builder.git.enabled=true` (the default). Without a git repo,
  `/api/dsl/vcs/status` returns 404, every definition resolves to `PUBLISHED`, and the
  git operations return `GIT_NOT_CONFIGURED`.
- Add `build/` and `.gradle/` to the workspace repo's `.gitignore` (I3).
- To publish to a shared repo, set `cbs.dsl.builder.git.push-on-commit=true` and make sure the
  `git.remote` is reachable with the credentials dsl-builder runs under.

---

## 8. Configuration

All keys use the `cbs.dsl.` prefix.

| Key | Default | Service | Purpose |
|-----|---------|---------|---------|
| `cbs.dsl.drafts.enabled` | `true` | starter | Registers `DslDraftHandler` and its routes |
| `cbs.dsl.source-dir` | – | starter | DSL source dir. Required by the draft handlers. |
| `cbs.dsl.approval.required` | `false` | starter | Publish approval gate |
| `cbs.dsl.builder-client.base-url` | `http://localhost:8091` | starter | Delegate to dsl-builder |
| `cbs.dsl.builder.workspace-mode` | `SHARED` | dsl-builder | Workspace mode: `SHARED` (source-dir, dev/local) or `WORKTREE` (builder-managed worktree, §1.1) |
| `cbs.dsl.builder.workspace-dir` / `source-dir` | – | dsl-builder | Workspace root; in `SHARED` mode `source-dir` wins as the file/git root |
| `cbs.dsl.builder.git.{enabled,repository-dir,worktrees-dir,repo-url,sub-path,branch}` | `true`, … | dsl-builder | Worktree provisioning |
| `cbs.dsl.builder.git.status-cache-ttl-seconds` | `5` | dsl-builder | Status cache TTL |
| `cbs.dsl.builder.git.push-on-commit` / `remote` | `false` / `origin` | dsl-builder | Push after each publish commit |
| `cbs.dsl.builder.git.pull-strategy` | `REBASE` | dsl-builder | Divergence handling for `POST /api/dsl/vcs/pull`: `REBASE` (default; conflict aborts the rebase, tree untouched) or `MERGE` (creates a merge commit; conflict leaves the repo `MERGING`) |
| `cbs.dsl.builder.files.{flush-interval-seconds,max-queue-size,read-bulkhead-permits,write-bulkhead-permits}` | `5`, `100`, `32`, `8` | dsl-builder | Write buffer |
| `cbs.dsl.builder.bundles.{bundle-format-version,bundle-max-definitions}` | `1`, `200` | dsl-builder | Bundle format and size limit |

---

## 9. Testing

| Area | Tests |
|------|-------|
| Change classification, `RepoStatus` (incl. `sizes`) | `dsl-api` `vcs/GitChangeClassifierTest` |
| Builder git status + freshness | `GitStatusServiceTest`, `controller/VcsControllerTest` |
| Builder commit / discard / log / show / push | `WorkspaceGitServiceTest`, `controller/VcsControllerTest` |
| Builder files and flush | `FileServiceTest`, `FileBufferTest`, `FileBulkheadTest`, `controller/FileControllerTest` |
| Builder workspace mode / root | `service/WorkspaceServiceTest`, `controller/WorkspaceControllerTest`, `config/DslBuilderPropertiesBindingTest` |
| Starter git status | `service/DslGitStatusResolverTest` |
| Definition → source path, definition status | `service/DslSourcePathResolverTest`, `service/DslDefinitionStatusResolverTest` |
| Publish commit, discard, commits, git history / diff / restore | `DslDraftGitResourceTest` |
| Draft routes | `DslDraftResourceTest` |
| Drafts metadata | `DslDraftMetadataResourceTest`, `components/.../DraftMetadataCard.spec.ts`, BFF `drafts-metadata.spec.ts` |
| Approval gate, change requests | `DslDraftApprovalGateTest`, `approval/ChangeRequestServiceTest`, `controller/ChangeRequestHandlerTest` |
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

## 10. Code map

| Concern | Starter (`backend/dsl-starter/starter/.../cbs/nova/starter`) | dsl-builder (`backend/dsl-plugins/dsl-builder/.../cbs/nova/dsl/builder`) |
|---------|------|------|
| Shared git model | `dsl-api` `cbs.nova.dsl.vcs` (`ChangeType`, `RepoStatus`, `GitChangeClassifier`) | same |
| Draft HTTP | `controller/DslDraftHandler`, `config/router/DslDraftRouterConfiguration` | – |
| Git status | `service/DslGitStatusResolver` | `service/GitStatusService`, `controller/VcsController` |
| Git operations | `builder/DslBuilderClient.commit/discard/log/show` | `service/WorkspaceGitService` |
| Definition → source path | `service/DslSourcePathResolver` | – |
| Definition status | `service/DslDefinitionStatusResolver`, `model/DslIntrospectionModels.DefinitionStatus` | – |
| Files | `controller/DslFileHandler`, `service/DslFileService` | `controller/FileController`, `service/FileService`, `FileBuffer`, `FileBulkhead` |
| Change requests / approval | `controller/ChangeRequestHandler`, `service/ChangeRequestService`, `entity/ChangeRequestEntity`, `config/router/ChangeRequestRouterConfiguration` | – |
| Bundles | `service/DslDefinitionBundleService` | – |
| Compile / reload | `controller/DslReloadHandler` | `controller/CompileController`, `service/CompileService`, `GradleService` |
| Clone / worktree | – | `service/GitService`, `RepoUrlValidator` |
| Workspace mode / root | – | `service/WorkspaceService`, `controller/WorkspaceController` |
| Remote client | `builder/DslBuilderClient`, `BuilderCache`, `config/BuilderClientConfiguration` | – |
