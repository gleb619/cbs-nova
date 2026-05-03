# BPMN Export

← [Back to TDD](../tdd.md)

## 12. BPMN Export

### 12.1 Purpose

Workflow definitions and execution instances can be exported as BPMN 2.0 XML and visualized via `bpmn-js` in the admin
panel — similar to Camunda/Flowable process visualization. This gives stakeholders a familiar process map rather than a
raw state machine diagram.

### 12.2 Two Export Modes

**Static (workflow template):**
Generated from the Workflow DSL definition. Shows the complete state machine as a BPMN diagram — all states as
tasks/gateways, all transitions as sequence flows. No execution data. Used for documentation and process review.

**Dynamic (execution instance heatmap):**
Generated from a specific `workflow_execution` + `workflow_transition_log`. Overlays the static diagram with:

- Current state (highlighted node)
- Route taken (visited transitions highlighted)
- Heatmap overlay: transition frequency across all instances (color intensity = volume)
- FAULTED states marked with error indicators

This mirrors the Camunda/Flowable instance detail view — stakeholders can see exactly which path a specific loan
contract took, and where most contracts drop off across the portfolio.

### 12.3 Export Approach

The BPMN XML is generated server-side from the DSL model:

```
WorkflowDefinition → BpmnExporter → BPMN 2.0 XML
                                         │
                              ┌──────────┴──────────┐
                              ▼                     ▼
                     Static template         Dynamic instance
                     (from DSL only)         (+ transition_log data)
```

States map to BPMN `<userTask>` or `<serviceTask>` elements. Transitions map to `<sequenceFlow>` with conditions. Terminal states map to `<endEvent>`. FAULTED state maps to `<boundaryErrorEvent>`.

### 12.4 Frontend Integration

The admin panel embeds `bpmn-js` for rendering. Two tabs per workflow view:

- **Process Map** — static BPMN from template export
- **Instance View** — dynamic BPMN with route highlight and heatmap overlay for a specific `eventNumber` or aggregate across all instances of a workflow code

API endpoints:

```
GET /api/workflows/{code}/bpmn                    → static BPMN XML
GET /api/workflows/{code}/bpmn/{eventNumber}      → dynamic BPMN XML for one instance
GET /api/workflows/{code}/bpmn/aggregate          → heatmap BPMN XML across all instances
```

---

The v0.5 release retains the same three BPMN endpoints:

```
GET /api/workflows/{code}/bpmn
GET /api/workflows/{code}/bpmn/{eventNumber}
GET /api/workflows/{code}/bpmn/aggregate
```
