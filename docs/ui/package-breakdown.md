# Package Breakdown

← [Back to TDD](../tdd.md)

---

## 4. Package Breakdown

### 4.1 `packages/admin-core` — Runtime Engine

```
packages/admin-core/src/
├── index.ts
├── plugin.ts
├── composables/
│   ├── useAdminEntity.ts       # Core CRUD composable
│   ├── useRsqlFilter.ts        # RSQL query builder composable
│   ├── useQuickFilter.ts       # Quick filter (above-table) state
│   ├── useSearchSidebar.ts     # Search sidebar state + AND/OR logic
│   ├── useSavedSearches.ts     # Load/save/delete saved searches (BFF)
│   ├── useColumnVisibility.ts  # Per-table per-user column prefs (localStorage)
│   ├── useRelationPicker.ts    # Combobox + modal picker logic
│   ├── useAbac.ts              # ABAC/RBAC from Keycloak token
│   ├── useAdminForm.ts         # Form state + Zod validation
│   └── useWorkflowWidget.ts    # Workflow execution widget for business entities
├── stores/
│   ├── auth.store.ts           # Keycloak state (Pinia)
│   ├── entity.store.ts         # Generic entity cache (TanStack Query wrapper)
│   └── ui.store.ts             # Sidebar open/close, breadcrumbs, active group
├── services/
│   ├── api.service.ts          # $fetch factory (BFF-aware, attaches token)
│   ├── keycloak.service.ts     # Keycloak-js wrapper
│   └── rsql.builder.ts         # Fluent RSQL query builder
└── types/
    ├── entity.types.ts
    ├── filter.types.ts
    ├── abac.types.ts
    ├── config.types.ts
    ├── workflow.types.ts        # WorkflowExecution, EventExecution, TransitionLog
    └── search.types.ts          # SavedSearch, SearchScope
```

### 4.2 Core Config Types

```typescript
// types/config.types.ts

export interface AdminConfig {
  api: {
    baseUrl: string;           // points to Nuxt BFF, e.g. '/api'
    timeout?: number;
  };
  auth: {
    keycloakUrl: string;
    realm: string;
    clientId: string;
  };
  entities: EntityRegistration[];
  layout?: {
    logo?: string;
    appName?: string;
    sidebarGroups?: SidebarGroup[];
  };
  i18n?: {
    defaultLocale: string;
    locales: string[];
  };
}

export interface SidebarGroup {
  key: string;
  label: string;                 // i18n key
  icon?: string;
  roles?: string[];              // ABAC: hide group if user has none of these roles
}

export interface EntityRegistration {
  name: string;                  // 'loan' — used as route param and i18n key prefix
  label: string;                 // i18n key: 'entities.loan.label'
  apiPath: string;               // '/loans' — BFF path
  icon?: string;
  group?: string;                // references SidebarGroup.key
  formMode?: 'drawer' | 'page'; // override auto-detection
  fields: AdminFieldConfig[];
  permissions?: EntityPermissions;
  customRoute?: string;          // '/admin/loans' — use instead of generic route
}

export interface AdminFieldConfig {
  key: string;
  label: string;                 // i18n key
  type: FieldType;
  filterable?: boolean;
  sortable?: boolean;
  showInList?: boolean;          // default columns in datatable
  showInForm?: boolean;
  readonly?: boolean;
  relation?: RelationConfig;
  abac?: AbacFieldRule;
  validation?: ZodTypeAny;
}

export type FieldType =
  | 'string'
  | 'number'
  | 'boolean'
  | 'date'
  | 'datetime'
  | 'enum'
  | 'json'                       // JSONB fields — rendered as JSON editor
  | 'relation-one'
  | 'relation-many';

export interface RelationConfig {
  entity: string;
  labelField: string;
  valueField?: string;           // default: 'id'
  searchable?: boolean;
  preload?: boolean;             // preload for small lookup tables (currencies, etc.)
  modalPicker?: boolean;         // show "advanced" button to open modal datatable
}

export interface EntityPermissions {
  read?: string[];
  create?: string[];
  update?: string[];
  delete?: string[];
}

export interface AbacFieldRule {
  read?: string[];
  write?: string[];
}
```

### 4.3 Form Mode Auto-Detection

At codegen time, `formMode` is determined by field count:

```typescript
// admin-codegen/src/schema-parser.ts
function resolveFormMode(fields: AdminFieldConfig[]): 'drawer' | 'page' {
  const formFields = fields.filter((f) => f.showInForm !== false);
  return formFields.length > 8 ? 'page' : 'drawer';
}
```

The threshold (8) is a codegen-time constant. Override via `formMode` in `EntityRegistration` if needed.

### 4.4 Workflow Types

```typescript
// types/workflow.types.ts

export interface WorkflowExecution {
  id: number;
  eventNumber: number;           // public-facing identifier
  workflowCode: string;
  dslVersion: string;
  currentState: string;
  status: 'ACTIVE' | 'CLOSED' | 'FAULTED';
  displayData: Record<string, string>;   // from DSL display {} block
  performedBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface EventExecution {
  id: number;
  eventCode: string;
  dslVersion: string;
  action: Action;
  status: 'DONE' | 'FAULTED' | 'RUNNING';
  executedTransactions: TransactionResult[];
  temporalWorkflowId: string;
  workflowExecutionId: number;
  performedBy: string;
  createdAt: string;
  completedAt?: string;
}

export interface WorkflowTransitionLog {
  id: number;
  workflowExecutionId: number;
  eventExecutionId?: number;
  action: Action;
  fromState: string;
  toState?: string;
  status: 'RUNNING' | 'COMPLETED' | 'FAULTED';
  faultMessage?: string;
  dslVersion: string;
  performedBy: string;
  createdAt: string;
  completedAt?: string;
}

export type Action =
  | 'PREVIEW'
  | 'SUBMIT'
  | 'APPROVE'
  | 'REJECT'
  | 'CANCEL'
  | 'CLOSE'
  | 'ROLLBACK';

export interface TransactionResult {
  transaction: string;
  status: 'EXECUTED' | 'ROLLED_BACK' | 'SKIPPED';
}

// Minimal shape returned by workflow widget on business entity pages
export interface WorkflowWidget {
  eventNumber: number;
  workflowCode: string;
  currentState: string;
  status: 'ACTIVE' | 'CLOSED' | 'FAULTED';
  displayData: Record<string, string>;   // key display fields from DSL
  availableActions: Action[];            // actions valid for current state
}
```

### 4.5 Saved Search Types

```typescript
// types/search.types.ts

export interface SavedSearch {
  id: number;
  name: string;
  entityName: string;
  rsqlQuery: string;              // compiled RSQL string
  filterState: Record<string, unknown>; // raw filter form state for UI restore
  scope: 'ALL' | 'USER';
  visibleToUserIds?: string[];    // populated when scope is specific users (future)
  createdBy: string;
  createdAt: string;
}
```
