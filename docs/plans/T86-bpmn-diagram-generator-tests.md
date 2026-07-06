# T86 — BPMN diagram generator tests

## Goal
Add unit tests for `BpmnDiagramGenerator` covering process/transaction/helper entry points, compensation gateway path, and call-count annotation. Locks the BPMN output contract used by `ExplainReport` and the execution details UI.

## Tier
backend

## Files to create / modify
- Create: `backend/dsl/src/test/java/cbs/nova/dsl/BpmnDiagramGeneratorTest.java`
- Read only: `backend/dsl/src/main/java/cbs/nova/dsl/BpmnDiagramGenerator.java`

## Acceptance criteria
- `forProcess` without compensation produces a valid BPMN XML containing the activity name and an end event.
- `forProcess` with compensation includes `Compensate` activity and success/fail sequence flows.
- `forTransaction` produces a BPMN XML containing the transaction name.
- `forHelper` produces a BPMN XML containing the helper name.
- Call counts are rendered as an XML comment before `</bpmn:definitions>` when provided.
- `./gradlew :dsl:test --tests '*BpmnDiagramGeneratorTest*'` passes.

## Build / test commands
```bash
cd backend
./gradlew spotlessApply
./gradlew :dsl:test --tests '*BpmnDiagramGeneratorTest*'
```
