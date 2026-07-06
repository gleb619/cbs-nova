# T73 — DSL input JSON Schema generation

**Tier:** backend
**Status:** Backlog
**Owner:** loop

## Goal

Generate JSON Schema (Draft 2020-12) from a DSL definition's input shape — either `ParameterDescriptor` list or typed input class — and expose it via the introspection REST endpoint. The Runner UI currently guesses the input shape in `extractDefinitions`; providing a schema lets `InputForm` render typed controls reliably.

## Acceptance Criteria

- [ ] Add `JsonSchemaGenerator` in `backend/dsl` that converts a `List<ParameterDescriptor>` into a JSON Schema object.
- [ ] Support `ParameterType` values: STRING, NUMBER, BOOLEAN, OBJECT (with `objectType` optional), LIST (if added), and mark required fields.
- [ ] For typed processes/transactions with `inputType` record, generate schema from record fields (fallback to empty object if reflection fails).
- [ ] Expose schema on `ProcessDetail` / `TransactionDetail` records in `DslIntrospectionResource`.
- [ ] Update the BFF `/api/v1/dsl/definitions` route or introspection composable so the frontend receives `inputSchema` per definition.
- [ ] `./gradlew spotlessApply && ./gradlew :dsl:test :starter:test` passes.
- [ ] Update `docs/dsl/authoring.md` with a note that parameter-based definitions auto-generate JSON Schema for the Runner UI.

## Files to Create / Modify

- `backend/dsl/src/main/java/cbs/nova/dsl/JsonSchemaGenerator.java` — new.
- `backend/dsl/src/test/java/cbs/nova/dsl/JsonSchemaGeneratorTest.java` — new.
- `backend/starter/src/main/java/cbs/nova/starter/DslIntrospectionResource.java` — add schema field to detail records.
- `frontend/admin-ui/app/composables/useDslApi.ts` or `runner.vue` — consume `inputSchema`.

## Build / Test Commands

```bash
cd backend
./gradlew spotlessApply
./gradlew :dsl:test :starter:test
```
