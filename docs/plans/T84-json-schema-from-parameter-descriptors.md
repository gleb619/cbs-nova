# T84 — DSL input JSON Schema generation

## Goal
Implement backend JSON Schema generation for DSL definition inputs. For typed definitions, derive a minimal schema from `inputType`; for parameter-based definitions, build Draft 2020-12 schema from `List<ParameterDescriptor>`. Expose it through a new field on `ProcessDetail`/`TransactionDetail` in `DslIntrospectionResource` so the Runner UI (T58) can render `InputForm` from schema instead of guessing shape.

## Tier
backend

## Files to create / modify
- Create: `backend/starter/src/main/java/cbs/nova/starter/JsonSchemaGenerator.java`
- Modify: `backend/starter/src/main/java/cbs/nova/starter/DslIntrospectionResource.java` — add `inputSchema` to detail records
- Modify: `backend/starter/src/test/java/cbs/nova/starter/DslIntrospectionResourceTest.java` — assert schema presence
- Read only: `backend/dsl-api/src/main/java/cbs/nova/dsl/ParameterDescriptor.java`, `backend/dsl-api/src/main/java/cbs/nova/dsl/ParameterType.java`

## Acceptance criteria
- JSON Schema for typed process/tx includes `type: object`, `properties` from record fields if available, and `$schema`.
- JSON Schema for parameter-based process/tx maps `ParameterType` to JSON Schema types (`string`, `number`, `boolean`, `object`).
- `DslIntrospectionResource` detail endpoints include `inputSchema`.
- `./gradlew :starter:test --tests '*DslIntrospectionResourceTest*'` passes.
- `./gradlew spotlessApply` leaves formatting clean.

## Build / test commands
```bash
cd backend
./gradlew spotlessApply
./gradlew :starter:test --tests '*DslIntrospectionResourceTest*'
```
