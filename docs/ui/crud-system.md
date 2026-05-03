# Generic CRUD System

← [Back to TDD](../tdd.md)

---

## 5. Generic CRUD System

### 5.1 Route Structure (Generic)

The `admin-ui` Nuxt layer provides generic pages. The host app only creates pages when overriding.

```
packages/admin-ui/pages/admin/
└── [entity]/
    ├── index.vue         # List page → <AdminEntityList />
    ├── create.vue        # Create form → <AdminEntityForm mode="create" />
    └── [id].vue          # Edit form → <AdminEntityForm mode="edit" />
```

For entities with `formMode: 'drawer'`, create and edit are handled within `index.vue` — the drawer opens on top of the
list. The `create.vue` and `[id].vue` routes still exist as fallbacks but redirect to the list with the drawer open.

For entities with `formMode: 'page'`, navigation goes to a full separate page.

### 5.2 Custom Route Override

When an entity needs a handmade page, set `customRoute` in its `EntityRegistration`. The sidebar link and any internal
navigation use this route instead of the generic one.

```typescript
// admin.config.ts
{
  name: 'workflow-execution',
  label: 'entities.workflowExecution.label',
  apiPath: '/workflow-executions',
  group: 'orchestration',
  customRoute: '/admin/orchestration/executions',  // → handmade page
  permissions: { read: ['ROLE_ADMIN', 'ROLE_OPS'] },
  fields: [],  // fields still used for column visibility + search config
}
```

### 5.3 `useAdminEntity` Composable

```typescript
// composables/useAdminEntity.ts
export function useAdminEntity(entityName: string) {
  const config = computed(() =>
    adminConfig.entities.find((e) => e.name === entityName)
  );

  const { data, pending, refresh } = useAsyncData(
    `entity-list-${entityName}`,
    () => $fetch(`/api/${config.value?.apiPath}`, {
      params: {
        filter: activeRsql.value,
        page: currentPage.value,
        size: pageSize.value,
        sort: activeSort.value,
      },
    })
  );

  return { config, data, pending, refresh, /* ...pagination, sort state */ };
}
```
