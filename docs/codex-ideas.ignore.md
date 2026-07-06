# Codex Loop Ideas Log

Candidate ideas considered by `docs/codex-loop.ignore.md` each fire. One row per candidate (chosen + rejected). Used for dedup and for mining — a rejected idea may become a winner when code reality shifts.

## Legend

| Column       | Meaning                                                                 |
|--------------|-------------------------------------------------------------------------|
| ID-proposed  | Task ID the idea *would* take if chosen (`T<next>` placeholder)         |
| Title        | Short idea name                                                         |
| Category     | `helper` / `integration` / `test` / `docs` / `perf` / `dx` / `example` / `refactor` / `ui` |
| Outcome      | `Chosen` (added to kanban) or `Rejected` (not this cycle)               |
| Reason       | One line — value/risk/novelty verdict, or dup source                    |

Re-pick rule: a `Rejected` row is eligible for revival only if its `Reason` was risk/novelty at fire time and a later code reality shows the blocker is gone. Pure dup rejects never revive.

## Candidates

| ID-proposed | Title | Category | Outcome | Reason |
|-------------|-------|----------|---------|--------|
| T68 | Compensation context unit tests | test | Chosen | codegraph-confirmed no tests for CompensationRichContext/CompensationContext; low risk, high correctness value |
| T68 | Dashboard overview stats page | ui | Rejected | medium value but can wait until T59 execution page lands |
| T68 | Map helper for DSL data manipulation | helper | Rejected | useful but lower novelty than compensation tests |
| T68 | Shared useApiError composable | dx | Rejected | good DX improvement; defer until frontend test scaffold (T67) is in place |
| T68 | Transaction retry-policy validation tests | test | Rejected | small incremental value, already covered indirectly by builder/runtime tests |
| T69 | BPMN/Mermaid diagram generator tests | test | Chosen | codegraph-confirmed no tests for BpmnDiagramGenerator, thin Mermaid coverage; low risk, locks explain/runner UI contract |
| T69 | Dashboard overview stats page | ui | Rejected | medium value; defer until execution details UI (T59) lands |
| T69 | Map helper for DSL data manipulation | helper | Rejected | useful helper but lower priority than locking diagram contract |
| T69 | Shared useApiError composable | dx | Rejected | good DX; defer until frontend test scaffold (T67) in place |
| T69 | Transaction retry-policy validation tests | test | Rejected | small incremental value, already covered indirectly by builder/runtime tests |
| T70 | Dashboard overview page | ui | Chosen | T59 now Done; index.vue is still placeholder; fills last major nav gap, low risk |
| T70 | FunctionBuilder unit tests | test | Rejected | useful but smaller blast radius; DslBuilderTest already covers function basics |
| T70 | Shared useApiError composable | dx | Rejected | good DX; defer until frontend test scaffold (T67) in place |
| T70 | Map helper for DSL data manipulation | helper | Rejected | lower priority than finishing core admin pages |
| T70 | PropertyResolver edge-case tests | test | Rejected | PropertyResolver already has dedicated test class |
| T71 | DSL reload resource error-path tests | test | Chosen | codegraph-confirmed reload() method has no tests; low risk, completes admin reload endpoint contract |
| T71 | Frontend Vitest test scaffold | test | Rejected | already exists as T67 in Backlog; do not duplicate |
| T71 | Shared useApiError composable | dx | Rejected | good DX; defer until frontend test scaffold (T67) in place |
| T71 | Map helper for DSL data manipulation | helper | Rejected | lower priority than locking admin endpoint behavior |
| T71 | PropertyResolver edge-case tests | test | Rejected | PropertyResolver already has dedicated test class |
| T72 | Transaction registry and manager tests | test | Chosen | codegraph-confirmed no tests for DefaultTransactionRegistry/TransactionManager; low risk, completes runtime coverage |
| T72 | Frontend useApiError composable | dx | Rejected | useful DX; still deferred until frontend test scaffold (T67) lands |
| T72 | BFF DSL save/publish endpoints | backend | Rejected | needs backend DSL admin resource first; too broad for this cycle |
| T72 | Dashboard stats backend endpoint | backend | Rejected | can be client-side computed; backend endpoint adds infra |
| T72 | PlantUml diagram generator tests | test | Rejected | similar to T69 but smaller surface; defer until T69 done |
| T73 | DSL input JSON Schema generation | backend | Chosen | kanban row exists but stub plan file missing; completes T73 so it is actionable |
| T73 | Semantic validator tests | test | Rejected | good value but T73 is already queued and more user-facing |
| T73 | Frontend useApiError composable | dx | Rejected | still deferred until T67 frontend test scaffold lands |
| T73 | BFF error-status passthrough tests | test | Rejected | proxyToBackend has no tests yet, but needs test scaffold first |
| T73 | PlantUml diagram generator tests | test | Rejected | similar to T69; defer until T69 done |
| T74 | DSL exception handler tests | test | Chosen | DslException branch in handler untested; low risk, locks structured error REST contract |
| T74 | Frontend InputField/InputForm tests | test | Rejected | needs Vitest scaffold (T67) first |
| T74 | Shared useApiError composable | dx | Rejected | still deferred until T67 frontend test scaffold lands |
| T74 | Semantic validator tests | test | Rejected | valuable but DslException handler is more directly user-facing |
| T74 | PlantUml diagram generator tests | test | Rejected | similar to T69; defer until T69 done |
| T75 | Process manager and runner tests | test | Chosen | codegraph-confirmed no direct ProcessManager tests; low risk, locks core runtime dispatch |
| T75 | BFF proxy unit tests | test | Rejected | needs Vitest/Nitro test scaffold first; defer until T67 |
| T75 | Shared useApiError composable | dx | Rejected | still deferred until T67 frontend test scaffold lands |
| T75 | PlantUml diagram generator tests | test | Rejected | similar to T69; defer until T69 done |
| T75 | DSL advanced examples completion | example | Rejected | T64 already in progress; do not duplicate |
