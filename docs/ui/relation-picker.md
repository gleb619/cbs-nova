# Relation Picker System

← [Back to TDD](../tdd.md)

## 9. Relation Picker System

All relation fields use a two-tier picker system:

### 9.1 Tier 1 — Combobox (default)

Every entity exposes a `/lookup` BFF endpoint returning `{ id, title }` pairs. The combobox hits this endpoint with a
`?q=` search param.

```typescript
// composables/useRelationPicker.ts
export function useRelationPicker(entityName: string) {
  const search = ref('');
  const options = ref<{ id: unknown; title: string }[]>([]);

  watchDebounced(search, async (q) => {
    if (!q || q.length < 2) return;
    options.value = await $fetch(`/api/${entityName}/lookup?q=${q}`);
  }, { debounce: 250 });

  return { search, options };
}
```

The lookup endpoint on the BFF side:

```typescript
// server/api/[entity]/lookup.get.ts
// Returns [{id, title}] — title is the labelField defined in EntityRegistration
// Cached with Cache-Control: public, max-age=60 for stable lookup tables
```

### 9.2 Tier 2 — Modal Datatable Picker

When `relation.modalPicker: true` is set, or the user clicks "Advanced search" button in the combobox, a modal opens
containing a full mini-datatable of the related entity — same `AdminEntityList` component with reduced chrome, in a
modal wrapper. The user can filter, paginate, and select one or multiple records.

```vue
<!-- packages/admin-ui/components/Admin/RelationPicker/RelationPickerModal.vue -->
<template>
  <AppModal size="xl">
    <AdminEntityList
      :entity="relatedEntity"
      mode="picker"
      :multi="isMulti"
      @select="onSelect"
    />
  </AppModal>
</template>
```

When `mode="picker"` is passed to `AdminEntityList`, row click fires `select` event instead of navigating.

### 9.3 Preloaded Relations

For small stable lookup tables (currencies, statuses, document types) where `preload: true`:

```typescript
// The lookup result is fetched once at app boot and cached in entity.store.ts
// Subsequent combobox renders use the store, no API call
```
