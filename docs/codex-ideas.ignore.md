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
| T76 | Fix mobile drawer DSL Workbench nav link | ui | Chosen | codegraph-confirmed mismatch: mobile drawer /workbench vs sidebar /dsl-workbench; real 404 bug, low risk |
| T76 | HelperManager unit tests | test | Rejected | good coverage gap but frontend bug is user-facing and immediate |
| T76 | Shared useApiError composable | dx | Rejected | still deferred until T67 frontend test scaffold lands |
| T76 | BFF proxy error passthrough tests | test | Rejected | needs Nitro/Vitest scaffold; defer until T67 |
| T76 | PlantUml diagram generator tests | test | Rejected | similar to T69; defer until T69 done |
| T77 | HelperManager unit tests | test | Rejected | good coverage gap, but DslRuntimeResource endpoints are more user-facing and lower risk |
| T77 | ExternalCallTracker unit tests | test | Rejected | valuable for explain/runner reports, but runtime REST contract has higher user impact |
| T77 | DefaultHelperRunner run/error-path tests | test | Rejected | preview path already covered; smaller incremental value than run/explain REST coverage |
| T77 | DslRuntimeResource run/explain MockMvc tests | test | Chosen | codegraph-confirmed run/explain endpoints untested; locks user-facing REST contract; low risk MockMvc |
| T77 | Admin UI page head titles | ui | Rejected | nice polish but lower correctness value than backend runtime tests |
| T78 | HelperManager unit tests | test | Chosen | codegraph-confirmed HelperManager/DefaultHelperRunner lack direct tests; low risk, locks helper runtime contract |
| T78 | ExternalCallTracker unit tests | test | Rejected | useful for explain/runner reports, but helper runtime is more directly invoked by every DSL execution |
| T78 | Dashboard overview page | ui | Rejected | T70 already in backlog; do not duplicate |
| T78 | Shared nav items composable | ui | Rejected | nav duplication already queued as T76; do not duplicate |
| T78 | DefaultTransactionRegistry duplicate semantics | test | Rejected | already queued as T72; do not duplicate |
| T79 | ExternalCallTracker unit tests | test | Chosen | codegraph-confirmed no tests for ExternalCallTracker/Listener; locks observability contract for explain/runner; low risk |
| T79 | DslIntrospectionResource detail endpoint tests | test | Rejected | processes/tx detail paths partially covered; smaller incremental value |
| T79 | DslReloadResource success-path test | test | Rejected | error paths already queued as T71; success path needs real DSL sources on disk, infra risk |
| T79 | Runner page query sync tests | ui | Rejected | needs Vitest scaffold (T67) first; defer |
| T79 | Admin UI page head titles | ui | Rejected | nice polish; defer until core tests are in place |
| T80 | DslExceptionHandler DslException branch test | test | Chosen | handler DslException branch explicitly untested; low risk MockMvc, locks structured error contract |
| T80 | TemporalConfiguration bean wiring test | test | Rejected | already has a smoke test; smaller incremental value |
| T80 | DslWorkerConfiguration task queue wiring test | test | Rejected | existing test covers basic wiring; not a gap |
| T80 | Intermediate DSL examples explain-mode test | test | Rejected | large test already exists; adding explain assertions is bigger than one fire |
| T80 | Frontend Vitest test scaffold | test | Rejected | already queued as T67; do not duplicate |
| T81 | DefaultHelperRegistry unit tests | test | Chosen | codegraph-confirmed RegistryTest only touches DefaultHelperRegistry indirectly; low risk, completes registry coverage |
| T81 | CompensationRichContext tests | test | Rejected | already queued as T68; do not duplicate |
| T81 | ProcessRichContext delegation tests | test | Rejected | smaller surface; T68 and registry tests higher value |
| T81 | Frontend Vitest test scaffold | test | Rejected | already queued as T67; do not duplicate |
| T81 | Dashboard overview page | ui | Rejected | already queued as T70; do not duplicate |
| T82 | GlobalManager runtime gap tests | test | Chosen | codegraph-confirmed GlobalManagerTest lacks transaction/function round-trips and name sorting; low risk, locks facade contract |
| T82 | DefinitionLoader empty-directory path test | test | Rejected | empty dir returns empty list; smaller value than facade runtime contract |
| T82 | DevDslRuntime explain-mode report test | test | Rejected | explain report tests are broader; better grouped with T69 diagram tests |
| T82 | Frontend shared useApiError composable | dx | Rejected | still deferred until T67 frontend test scaffold lands |
| T82 | BFF proxy error passthrough tests | test | Rejected | needs Nitro/Vitest scaffold; defer until T67 |
| T83 | Frontend Vitest test scaffold revival | test | Chosen | T67 exists but stub plan was thin; reviving with concrete file list and commands to unblock all deferred frontend test tasks |
| T83 | Shared useApiError composable | dx | Rejected | T67 must land first; keep deferring |
| T83 | BFF proxy error passthrough tests | test | Rejected | T67 must land first; keep deferring |
| T83 | StatusIndicator unit test | test | Rejected | T67 must land first; keep deferring |
| T83 | ModeSwitcher unit test | test | Rejected | T67 must land first; keep deferring |
| T84 | DSL input JSON Schema generation | backend | Chosen | T73 existed as Backlog with missing stub plan; codegraph-confirmed ParameterDescriptor/MapInput ready; high user impact for Runner UI input forms |
| T84 | PropertyResolver failOnMissing edge cases | test | Rejected | PropertyResolverTest already exists; smaller value |
| T84 | DescriptorFactory helperRefs propagation | test | Rejected | helperRefs list is empty by design today; low value |
| T84 | ExecutionTraceCollector thread isolation | test | Rejected | already covered by existing test |
| T84 | Frontend Vitest test scaffold | test | Rejected | already queued as T67; do not duplicate |
| T85 | Transaction runner explain-mode tests | test | Chosen | codegraph-confirmed DefaultTransactionRunner lacks direct tests; low risk, locks transaction execution contract |
| T85 | Process runner EXPLAIN metadata tests | test | Rejected | already queued as part of T75; do not duplicate |
| T85 | Compensation context unit tests | test | Rejected | already queued as T68; do not duplicate |
| T85 | Frontend Vitest test scaffold | test | Rejected | already queued as T67; do not duplicate |
| T85 | Dashboard overview page | ui | Rejected | already queued as T70; do not duplicate |
| T86 | BPMN diagram generator tests | test | Chosen | codegraph-confirmed BpmnDiagramGenerator has no tests; complements T69 Mermaid coverage; low risk |
| T86 | PlantUml diagram generator tests | test | Rejected | similar to BPMN tests; defer until T86/T69 done |
| T86 | DevDslRuntime explain report integration test | test | Rejected | larger than one fire; needs diagram contracts locked first |
| T86 | Frontend Vitest test scaffold | test | Rejected | already queued as T67; do not duplicate |
| T86 | Dashboard overview page | ui | Rejected | already queued as T70; do not duplicate |
| T87 | Dashboard overview page revival | ui | Chosen | T70 exists but stub plan was thin; reviving with concrete components and data sources to close last major nav gap |
| T87 | useDslApi unit tests | test | Rejected | T67 must land first; keep deferring |
| T87 | InputForm schema-driven tests | test | Rejected | T67 must land first; keep deferring |
| T87 | BFF proxy error passthrough tests | test | Rejected | T67 must land first; keep deferring |
| T87 | DevDslRuntime explain failure path | test | Rejected | larger integration test; better after diagram tests locked |
