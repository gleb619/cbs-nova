# T69 — BPMN/Mermaid diagram generator tests

**Tier:** backend
**Status:** Backlog
**Owner:** loop

## Goal

`MermaidDiagramGenerator` and `BpmnDiagramGenerator` produce diagrams consumed by `DevDslRuntime.explain` and the Runner UI, yet they have no direct tests. Add focused unit tests that lock shape: nodes, compensation branches, external-call rendering, and call-count annotations.

## Acceptance Criteria

- [ ] `backend/dsl/src/test/java/cbs/nova/dsl/MermaidDiagramGeneratorTest.java` extended (already exists) or new tests added.
- [ ] New `backend/dsl/src/test/java/cbs/nova/dsl/BpmnDiagramGeneratorTest.java` created.
- [ ] Tests cover process/transaction/helper variants with and without compensation.
- [ ] Tests cover external-calls overloads: rendered nodes reference call type, operation, and truncated target.
- [ ] Tests cover call-count annotations in output.
- [ ] No production behavior change; tests only assert current generator output.
- [ ] `./gradlew spotlessApply && ./gradlew :dsl:test` passes.

## Files to Create / Modify

- `backend/dsl/src/test/java/cbs/nova/dsl/BpmnDiagramGeneratorTest.java` — new.
- `backend/dsl/src/test/java/cbs/nova/dsl/MermaidDiagramGeneratorTest.java` — extend with compensation + external call cases (or keep minimal if already adequate).

## Build / Test Commands

```bash
cd backend
./gradlew spotlessApply
./gradlew :dsl:test
```
