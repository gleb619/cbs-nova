# DSL Workbench

The **DSL Workbench** is the admin UI for authoring cbs-nova DSL definitions. It targets non-developer domain experts as well as engineers, so the interface favors forms, structured input, and immediate feedback over raw source editing.

A DSL definition is one of four constructs: **Process**, **Transaction**, **Function**, or **Helper**. Processes orchestrate Transactions, Helpers, and Functions. Transactions are single retryable actions. Functions and Helpers are lightweight reusable logic. See [DSL Constructs](../dsl/constructs.md) and [Authoring DSL Flows](../dsl/authoring.md) for the underlying model.

## 1. Construct explorer

The left sidebar lists all DSL constructs in the current module.

- **Grouping** — items are grouped by construct type: Processes, Transactions, Functions, Helpers.
- **Search** — filter by name, task queue, or referenced helper/transaction.
- **Status badges** — each row shows the effective state:
  - `Draft` — unsaved changes or never published.
  - `Valid` — passes compiler validation.
  - `Invalid` — has compiler errors.
  - `Published` — saved to the DSL module source.
- **Create action** — a single "New" button lets the user pick the construct type and opens it in the editor.
- **Selection** — clicking a construct opens it. Unsaved changes in the current editor are kept until explicitly discarded.

## 2. Page architecture

The page entry point is `frontend/admin-ui-plugin/app/pages/dsl-workbench.vue` (~600 LOC). Since the 2026-09-24 refactor it is a **thin orchestrator**: it instantiates the workbench store and API client, wires the composables together, and passes props/events to the extracted components. It owns no feature logic of its own beyond a small `syncSelectionEffects()` helper that fans a selection change out to the draft, source-file, structure, and doc composables.

### Composables

Feature logic lives in composables under `frontend/admin-ui-plugin/app/composables/`:

| Composable | Responsibility |
|---|---|
| `useDslWorkbench` | Central workbench store: constructs list, selection, create/validate/publish/delete, server autosave, definition reload. |
| `useWorkbenchSelection` | Selection flow: loads the backing source file, mirrors the selection into the drafts widget, and guards switches with the dirty-changes confirm. |
| `useWorkbenchDraft` | Editor draft body state plus debounced server-side autosave wiring. |
| `useDraftSave` | Save controller for the explicit save action (Ctrl+S) and its status pill. |
| `useDraftDirty` | Dirty-state tracker feeding the save-status pill. |
| `useWorkbenchDoc` | Docs tab content: load/save the markdown doc per selected construct. |
| `useWorkbenchStructure` | Structure tab data: fetch-on-select with retry. |
| `useWorkbenchObjectSearch` | Object search and helper catalog fetchers, including saved search filters. |
| `useWorkbenchPanels` | Persisted UI flags for the explorer and the four drawers. |
| `useWorkbenchSavedDrafts` | Adapter around `useSavedDrafts` so navbar draft-widget picks land in the page's selection flow. |
| `useWorkbenchDelete` | Delete-confirmation modal flow. |
| `useWorkbenchNewDefinition` | New-definition modal: name validation, template pick, create. |
| `useWorkbenchMenus` | Actions/Misc menu items and their handlers (publish, submit for approval, share link, panels). |
| `useWorkbenchShareLink` | Builds and copies a deep-link share URL for the selected construct. |
| `useWorkbenchDeepLink` | Incoming deep links: `?objectName=`, `?activeTab=`, and `?draft=` query params. |
| `useWorkbenchShortcuts` | Keyboard shortcuts (new / actions / misc menus). |
| `useWorkbenchBeforeUnload` | Unsaved-changes guards for page unload and route leave. |

### Extracted components

`frontend/admin-ui-plugin/app/components/workbench/` holds the four components extracted from the page:

- `WorkbenchHeader.vue` — top bar: construct label, pending-approval badge, menus, shortcuts.
- `WorkbenchEditorBanners.vue` — banners above the editor: file-backed notice, draft-restored/autosave-offline state, delete errors.
- `WorkbenchDrawers.vue` — right-hand drawers: helper catalog, publish history, diagnostics, definition tests.
- `WorkbenchNewDefinitionModal.vue` — the New-definition modal.

Reusable DSL widgets live in the shared `frontend/components/src/components/dsl/` package and are imported through `@cbs/components`.

## 3. Split editor layout

The workbench uses a fixed three-zone layout:

| Zone | Purpose |
|------|---------|
| **Sidebar** | Construct explorer (see above). |
| **Metadata panel** | Top section of the main area: name, version, types, task queue, retry policy. |
| **Body editor** | Bottom section of the main area: tabbed editor (structure, code, docs, preview, explain, hierarchy, problems). |

The metadata panel can be collapsed to give the body editor more room. A status bar at the bottom shows the last validation result and the current save/publish state.

## 4. Metadata panel

Metadata fields depend slightly on the construct type.

Common fields for every construct:

- **Name** — unique identifier; immutable after first publish for a given construct.
- **Version** — optional human-readable label (e.g., `1.0.0`).

Process and Transaction fields:

- **Task queue** — the Temporal task queue the generated worker will listen on.
- **Retry policy** — `maxAttempts`, `initialInterval`, `backoffCoefficient`, `maximumInterval`.
- **Start-to-close timeout** — activity timeout for Transactions.

Input/output specification (one of two modes):

- **Typed** — select an existing `@Json` record class for input and another for output.
- **Parameter-based** — define a flat schema of fields (`string`, `number`, `boolean`, `date`, `map`, `list`).

The panel validates that typed records are visible to the DSL module and that parameter names are unique. Function and Helper editors omit task queue, timeout, and retry policy because they run locally.

## 5. Body editor tabs

The body editor (`BodyEditor.vue`) hosts seven tabs (implemented as `StructureTab`, `CodeTab`, `DocTab`, `PreviewTab`, `ExplainTab`, `HierarchyTab`, and the `ProblemsPanel`):

### Structure tab

A form-driven outline of the definition:

- For a **Process**: ordered steps, where each step is a helper call, function call, transaction call, or local variable assignment.
- For a **Transaction**: the same step types, plus the optional compensation block.
- For a **Function** or **Helper**: a single sequence of steps.

Each step row shows the target name, input mapping, and assigned output variable. Steps can be reordered by drag and drop, duplicated, or deleted.

### Code tab

An editable code view for the `execute` block. The editor is pre-filled from the structure tab and kept in sync:

- Editing the structure rewrites the code skeleton.
- Editing the code updates the structure when the user switches back, if the code is parseable.
- Unparseable code keeps the structure tab in its last known good state and shows an error.

The code view supports syntax highlighting for the DSL builder calls (`runHelper`, `runTransaction`, `fail`, `log`, `complete`, `withBody`) and basic autocomplete for variable names and field accessors. Refresh and Validate live in this tab's toolbar.

### Docs tab

A markdown editor for the per-construct documentation file, loaded and saved by construct name.

### Preview / Explain / Hierarchy tabs

Runner-backed tabs that send the current definition body to the backend and render the result:

- **Preview** — dry-run the construct and inspect inputs/outputs.
- **Explain** — human-readable explanation of what the construct does.
- **Hierarchy** — call/reference hierarchy of the construct (which helpers, functions, and transactions it invokes, recursively).

### Problems tab

A collapsible drawer listing every error and warning with a clickable location.

## 6. Inline validation feedback

Validation runs automatically after each meaningful change and on explicit request.

Feedback is shown in three places:

1. **Inline markers** — red underlines and tooltips on fields, step rows, and code lines.
2. **Problems panel** — a collapsible drawer listing every error and warning with a clickable location.
3. **Status bar** — a short summary such as "3 errors" or "Valid".

The workbench delegates validation to the DSL compiler. Typical messages include:

- Unknown helper or transaction name.
- Type mismatch in a `runHelper` or `runTransaction` call.
- Missing parameter referenced in `execute` but not declared in metadata.
- Compensation block invoking a Transaction or Process.
- Function calling a Process or Transaction.
- Duplicate construct name.

Warnings appear for discouraged patterns, such as a Process without a compensation block when one of its Transactions declares compensation.

## 7. Drafts, publish, and history

The workbench distinguishes between local editor state and committed DSL source. Drafts are **not** stored as separate workbench records — a draft is a git-changed DSL source file.

### Draft model

- A draft exists when the DSL source file backing a definition differs from the last committed state. The backend resolves git status for the source root (`DslGitStatusResolver`), so the draft list is exactly the set of changed definition files.
- Draft state is cached on the backend with a configurable TTL (`dslProperties.git().statusCacheTtlSeconds()`); the drafts metadata (`GET /api/v1/dsl/drafts/metadata`) reports the count, source path, size, branch, and cache TTL.
- The explorer shows DSL status badges per construct (Draft / Valid / Invalid / Published), resolved from git status plus definition status.
- The editor keeps a debounced server-side autosave of the in-progress body, plus a local draft restore banner and an offline/autosave-failed banner.

### Draft flows

- **Publish** — writes the construct to the DSL module source and rebuilds the module. Compiler diagnostics block the publish and surface in the problems panel; on success the handler refreshes the drafts list. Publish and approval submissions live in the Actions menu.
- **Discard** — `POST /api/v1/dsl/drafts/{name}/discard` reverts the changed file to the committed state.
- **Delete draft** — `DELETE /api/v1/dsl/drafts/{name}` removes a draft record for a definition that was never committed.
- **Commit history** — `GET /api/v1/dsl/drafts/{name}/commits` lists the commit history for a definition; the History drawer can diff and restore a past publish entry (restore triggers a definitions reload).
- **Drafts widget** — the navbar `DraftsWidget` lists git-changed definitions (`useSavedDrafts`); picking one navigates to the workbench and selects it.

BFF proxy routes for all draft endpoints live under `frontend/admin-ui-plugin/server/api/v1/dsl/drafts/`.

## 8. Helper and transaction reference picker

When a step references a Helper, Function, or Transaction, the user opens a picker instead of typing the name.

The picker shows:

- **Name and type** — e.g., `riskAssessment` (Helper) or `KYC_CHECK` (Transaction).
- **Input/output signature** — typed records or parameter schema.
- **Availability** — only references that are legal from the current construct are selectable. For example, a Function editor cannot pick a Transaction.
- **Description** — sourced from the `explain` metadata of Helpers when available.

After selection, the workbench proposes an input mapping:

- **Auto-resolve** — map fields from the current context body when names and types match.
- **Explicit map** — open a small mapping grid to connect each target input field to a constant, context field, or prior step output.

The picker also supports creating a new Helper or Function stub directly from the body editor when a needed reference does not yet exist.
