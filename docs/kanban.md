# Kanban Board

This file is a lightweight planner for tracking implementation tasks. It is intended to be used by the coding agent.

- **Use this file only as a high-level planner.** Detailed task descriptions, acceptance criteria, technical notes, and diagrams must be stored under [`./docs/plans`](./plans).
- The coding agent **must** update the `Status` column when taking a task and again when the work is finished.
- Keep the table sorted by `ID` for quick lookup.

## Status Legend

| Status       | Meaning                                              | Who updates it     |
| ------------ | ---------------------------------------------------- | ------------------ |
| `Backlog`    | Task is defined but not yet ready to start.            | Planner / Agent    |
| `Ready`      | Task is ready to be picked up by the coding agent.    | Planner / Agent    |
| `In Progress`| Coding agent is actively working on the task.        | Coding agent       |
| `Review`     | Implementation done; pending review / validation.   | Coding agent       |
| `Blocked`    | Work cannot continue until another task or issue is resolved. | Coding agent |
| `Done`       | Task completed and accepted.                         | Coding agent       |

## Task Board

| ID | Status | Title | Description | Priority | Owner | Blocks | Blocked By | Plan File |
| :- | :----- | :---- | :---------- | :------: | :---- | :----- | :--------- | :-------- |
| T32 | Done | DSL reload endpoint | POST /api/dsl/reload resets GlobalManager and re-loads DSL from dsl.source-dir | Medium | loop | - | - | `./docs/plans/T32-reload-endpoint.md` |
| T33 | Done | Global exception handler | @RestControllerAdvice catching unhandled exceptions, returns ErrorResponse with 500 | Medium | loop | - | - | `./docs/plans/T33-exception-handler.md` |
| T34 | Done | RetryPolicy builder flow tests | Tests verifying retryPolicy chains through TransactionBuilder → DslObject → TransactionDescriptor | Low | loop | - | - | `./docs/plans/T34-retry-policy-tests.md` |
| T35 | Done | OpenAPI integration | springdoc-openapi-starter-webmvc-ui + @Operation annotations + /swagger-ui active | Low | loop | - | - | `./docs/plans/T35-openapi.md` |
| T36 | Done | Fix DslReloadResource | Remove Spring bean injection of GlobalManager, add reset before load, add try/catch for 500 | High | loop | - | - | `./docs/plans/T36-fix-reload-resource.md` |
| T38 | Done | MapInput utility | MapInput.of(key, value, ...) ordered Map factory for parameter-based DSL calls | Medium | loop | - | - | `./docs/plans/T38-map-input.md` |
| T39 | Done | @Helper classpath scanner | DslAutoConfiguration scans dsl.helper-scan-packages for @Helper Executable classes | Medium | loop | - | - | `./docs/plans/T39-helper-scanner.md` |
| T40 | Done | Context sub-interfaces | ProcessContext, TransactionContext, CompensationContext interfaces in dsl-api | High | loop | T41 | - | `./docs/plans/T40-context-interfaces.md` |
| T41 | Done | RichContext impl + runner wiring | ProcessRichContext/TransactionRichContext impls + builder update + runner wiring | High | loop | - | T40 | `./docs/plans/T41-rich-context-impl.md` |
| T42 | Done | FunctionContext interface + FunctionRichContext | FunctionContext<T> in dsl-api + FunctionRichContext<T> in dsl + wire FunctionBuilder/FunctionDslObject/DefaultHelperRunner | Medium | loop | - | - | `./docs/plans/T42-function-context.md` |
| T43 | Done | ProcessContext.complete() + CompensationContext.log() fluent | Add complete(Object) to ProcessContext + make CompensationContext.log() return CompensationContext<T> | Low | loop | - | - | `./docs/plans/T43-process-complete-compensation-log.md` |
| T44 | Done | Parameter-based DSL support | ParameterRegistry/ParameterDescriptor/ParameterType in dsl-api + DefaultParameterRegistry + .parameters() in all 3 builders + update DslObject records | Medium | loop | - | - | `./docs/plans/T44-parameter-registry.md` |
| T45 | Done | Result.as() + Result.asMap() convenience methods | Add default as(Class<U>) and asMap() methods to Result<T> sealed interface | Low | loop | - | - | - |
| T46 | Done | Parameter DSL tests | DslBuilderTest cases for .parameters() on process/transaction/function + ResultTest cases for as/asMap | Low | loop | - | T45 | - |
| T47 | Done | ResultTest as/asMap coverage | Add ResultTest cases for Result.as() and Result.asMap() default methods | Low | loop | - | - | - |
| T48 | Done | heartbeatTimeout on TransactionBuilder | Add heartbeatTimeout(Duration) to TransactionBuilder + TransactionDslObject record + TransactionDescriptor | Low | loop | - | - | - |

> **How to use:** Replace the example rows with real tasks. Create a matching plan file under `./docs/plans/<ID>-short-title.md` for each task that needs detailed instructions.
