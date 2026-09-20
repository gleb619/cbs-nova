# T625 — BpmnGraphDiagram unit spec

## Goal

Add unit specs for `BpmnGraphDiagram` (192 LOC, `dsl-api/model`) — the graph builder behind
hierarchy explain diagrams (sole consumer: `HierarchyDiagrams`).

## Why

dsl-api is the thinnest-tested platform module (100 main / 16 test classes). `BpmnGraphDiagram`
builds BPMN/graph output from hierarchy data; zero test references anywhere. Layout/builder
regressions silently corrupt explain diagrams.

## Acceptance criteria

- [ ] Happy path: node/edge construction from a representative hierarchy (process + helpers +
      transactions).
- [ ] Edge cases: empty graph, single node, deep nesting, shared/duplicate nodes, cycles if
      supported.
- [ ] Output assertions on structure (node ids, edge pairs, labels) — characterization style,
      pin current behavior.
- [ ] Companion `HierarchyDiagrams` covered at least via one integration-style assertion if
      cheap.
- [ ] `make lint` passes; targeted test run green.

## Tier

`backend`

## Files to create/modify

- Create: `backend/dsl-platform/dsl-api/src/test/java/cbs/nova/dsl/model/BpmnGraphDiagramTest.java`
- Read first: `BpmnGraphDiagram.java`, `HierarchyDiagrams.java`, sibling model tests for style.

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-platform :dsl-api:test
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Tests only — no diagram logic changes.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
