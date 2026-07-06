# T93 — Schema Field on Definition Details

## Goal
Wire `inputSchema` into `DslIntrospectionResource` `ProcessDetail` / `TransactionDetail` records and the BFF `/api/v1/dsl/definitions` route so the Runner UI can render typed `InputForm` from schema instead of guessing shape.

## Acceptance Criteria
- `DslIntrospectionResource` detail records include an `inputSchema` field (Map/String/JsonNode) for every returned process/transaction.
- For typed definitions, `inputSchema` reflects the input record shape minimally; for parameter-based definitions, it derives from `List<ParameterDescriptor>`.
- Backend unit test asserts detail JSON contains `inputSchema` for at least one typed and one parameter-based definition.
- BFF `/api/v1/dsl/definitions` proxies the schema field through unchanged.
- `./gradlew spotlessApply && ./gradlew :starter:test` passes.

## Tier
`backend`

## Files to Create / Modify
- Modify: `backend/starter/src/main/java/cbs/nova/starter/DslIntrospectionResource.java` — add `inputSchema` to detail records.
- Modify: `backend/starter/src/test/java/cbs/nova/starter/DslIntrospectionResourceTest.java` — assert schema presence.
- Modify: `frontend/admin-ui/server/api/v1/dsl/definitions.get.ts` — ensure field is forwarded (likely already transparent).
- Optional: create `backend/starter/src/main/java/cbs/nova/starter/JsonSchemaGenerator.java` if no schema generator exists yet.

## Build / Test Commands
Run from `backend/`:

```bash
./gradlew spotlessApply
./gradlew :starter:test --tests '*DslIntrospectionResourceTest*'
```
