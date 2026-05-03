# Navigation, Sidebar & ABAC Access Control

← [Back to TDD](../tdd.md)

## 12. Navigation & Sidebar

### 12.1 Sidebar Structure

```
┌─────────────────────┐
│  CBS Admin          │
│  ─────────────────  │
│  📊 Dashboard       │
│                     │
│  ▼ Finance          │
│    💰 Loans         │
│    💱 Currencies    │
│    📋 Accounts      │
│                     │
│  ▼ Operations       │
│    🏢 Branches      │
│    📅 Calendar      │
│    📚 Dictionaries  │
│                     │
│  ▼ Orchestration    │
│    ⚙️  Executions    │
│    📋 Event Log     │
│    🔗 DSL Rules ↗   │ ← external link, ROLE_DEVELOPER only
│    🔗 Temporal UI ↗ │ ← external link, ROLE_OPS+
│                     │
│  ▼ System           │
│    ⚙️  Settings      │
│    📖 Dictionaries  │
│    👥 Users         │
└─────────────────────┘
```

### 12.2 Sidebar Config in `admin.config.ts`

```typescript
layout: {
  appName: 'CBS Admin',
  sidebarGroups: [
    { key: 'finance',       label: 'sidebar.finance',       icon: 'banknotes',   roles: ['ROLE_ADMIN', 'ROLE_BA'] },
    { key: 'operations',    label: 'sidebar.operations',    icon: 'building',    roles: ['ROLE_ADMIN', 'ROLE_OPS'] },
    { key: 'orchestration', label: 'sidebar.orchestration', icon: 'cpu-chip',    roles: ['ROLE_ADMIN', 'ROLE_OPS', 'ROLE_DEVELOPER'] },
    { key: 'system',        label: 'sidebar.system',        icon: 'cog',         roles: ['ROLE_ADMIN'] },
  ],
}
```

### 12.3 ABAC Sidebar Filtering

```typescript
// composables/useAbac.ts
export function useAbac() {
  const auth = useAuthStore();

  // Returns only groups the user has at least one matching role for
  function visibleGroups(groups: SidebarGroup[]): SidebarGroup[] {
    return groups.filter((g) =>
      !g.roles || g.roles.some((r) => auth.roles.includes(r))
    );
  }

  // Returns only entities the user can READ
  function visibleEntities(entities: EntityRegistration[]): EntityRegistration[] {
    return entities.filter((e) =>
      !e.permissions?.read || e.permissions.read.some((r) => auth.roles.includes(r))
    );
  }

  function can(action: 'READ' | 'CREATE' | 'UPDATE' | 'DELETE', entity: string): boolean { ... }
  function canField(field: string, entity: string, action: 'READ' | 'WRITE'): boolean { ... }

  return { visibleGroups, visibleEntities, can, canField };
}
```

---

## 13. ABAC — Access Control

ABAC is evaluated **client-side** from Keycloak token claims. No separate permission API call.

### 13.1 Levels

| Level          | What it controls                | Where evaluated                                   |
|----------------|---------------------------------|---------------------------------------------------|
| Sidebar group  | Group visibility                | `useAbac().visibleGroups()` in `AdminSidebar.vue` |
| Entity         | List/create/edit/delete buttons | `useAbac().can()` in `AdminEntityActions.vue`     |
| Field          | Column in table, field in form  | `useAbac().canField()` in field components        |
| External links | DSL Rules, Temporal UI          | `v-if="abac.can('READ', 'dsl-rules')"`            |

### 13.2 Permission Evaluation Order

1. Sidebar group `roles` → hide entire group if no match
2. Entity `permissions.read` → hide entity from sidebar if no read access
3. Entity `permissions.create/update/delete` → hide action buttons
4. Field `abac.read/write` → hide column / disable form field

### 13.3 Route Guard

```typescript
// packages/admin-ui/middleware/permission.ts
export default defineNuxtRouteMiddleware((to) => {
  const entityName = to.params.entity as string;
  const config = getEntityConfig(entityName);
  const abac = useAbac();

  if (config && !abac.can('READ', entityName)) {
    return navigateTo('/admin/forbidden');
  }
});
```
