# Handmade Pages — Orchestration UI

← [Back to TDD](../tdd.md)

## 10. Handmade Pages — Orchestration UI

### 10.1 Workflow Execution List Page

Route: `/admin/orchestration/executions`

Standard datatable using `AdminEntityList` with `customRoute` config, but the entity registration points to the handmade
page. The list shows: `eventNumber`, `workflowCode`, `currentState` (badge), `status` (badge), `performedBy`,
`createdAt`. Clicking a row navigates to the execution detail page.

### 10.2 Workflow Execution Detail Page

Route: `/admin/orchestration/executions/[eventNumber]`

Three-zone layout:

```
┌─────────────────────────────────────────────────────────────────────┐
│  ZONE 1 — TOP BAR                                                   │
│  Event #10042  LOAN_CONTRACT  [ENTERED → ACTIVE]  v1.5.0-a3f91bc   │
│  Performed by: operator@bank.kz  Created: 2025-04-08 14:32         │
│  [APPROVE ▾]  [CANCEL]  [ROLLBACK]   ← context-sensitive actions   │
└─────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────┬──────────────────────────────────────┐
│  ZONE 2A — BPMN DIAGRAM      │  ZONE 2B — TABBED PANEL             │
│  (bpmn-js, 60% width)        │  (40% width)                        │
│                              │  [Display] [Transactions] [Context]  │
│  Static BPMN from template   │            [Log]                    │
│  Current state highlighted   │                                      │
│  Visited transitions shown   │  Display tab:                        │
│                              │  Customer ID: C-001                  │
│                              │  Loan ID: L-9981                     │
│                              │  Amount: 500,000 KZT                 │
│                              │  Account: KZ123456789                │
│                              │                                      │
└──────────────────────────────┴──────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────────────┐
│  ZONE 3 — TRANSITION LOG (collapsible, collapsed by default)        │
│  ▶ Show transition history (4 entries)                              │
│                                                                     │
│  When expanded:                                                     │
│  SUBMITTED  ENTERED→ACTIVE  COMPLETED  2025-04-08 14:32  [details] │
│  ...each row expandable: fault_message, tx list, Temporal link      │
└─────────────────────────────────────────────────────────────────────┘
```

**Tab content:**

- **Display** — key/value pairs from `WorkflowExecution.displayData` (DSL `display {}` output). Business-meaningful
  labels. Read-only.
- **Transactions** — list from latest `EventExecution.executedTransactions`. Badge per status (EXECUTED / ROLLED_BACK /
  SKIPPED).
- **Context** — raw enriched context JSONB. Collapsible JSON viewer. Read-only. Visible only to `ROLE_DEVELOPER` /
  `ROLE_ADMIN`.
- **Log** — `WorkflowTransitionLog` entries for this execution, newest first. Each entry: action badge, from→to state,
  status badge, dsl version, performed by, timestamps. Expandable row: fault message, linked event execution id,
  Temporal UI link button.

**Action buttons (Zone 1):**
- Available actions derived from `WorkflowWidget.availableActions` (computed from current state + workflow DSL transitions)
- Actions requiring confirmation (CANCEL, ROLLBACK) show `AppConfirm` modal before calling BFF
- After action: page refreshes execution data; new state reflected in BPMN and top bar

### 10.3 Workflow Widget (on Business Entity Pages)

A compact embedded widget shown on business entity pages (e.g. loan detail page). Rendered from the aggregated BFF response.

```vue
<!-- components/Admin/Workflow/WorkflowWidget.vue -->
<template>
  <div class="workflow-widget">
    <div class="widget-header">
      <span class="event-number">#{{ widget.eventNumber }}</span>
      <StateBadge :state="widget.currentState" :status="widget.status" />
    </div>

    <div class="display-fields">
      <div v-for="(value, key) in widget.displayData" :key="key">
        <span class="label">{{ key }}</span>
        <span class="value">{{ value }}</span>
      </div>
    </div>

    <div class="widget-actions">
      <AppButton
        v-for="action in widget.availableActions"
        :key="action"
        size="sm"
        @click="triggerAction(action)"
      >
        {{ t(`workflow.actions.${action}`) }}
      </AppButton>
      <NuxtLink :to="`/admin/orchestration/executions/${widget.eventNumber}`">
        {{ t('workflow.viewDetails') }} →
      </NuxtLink>
    </div>
  </div>
</template>
```

Triggering an action from the widget calls the BFF action endpoint and then navigates to the full execution page.

### 10.4 DSL Rules Link (Sidebar)

A sidebar item in the Orchestration group. Renders as a plain `<a target="_blank">` pointing to
`{CODE_SERVER_HOST}/?folder=/workspace/cbs-rules`. Gated by `ROLE_DEVELOPER`.
