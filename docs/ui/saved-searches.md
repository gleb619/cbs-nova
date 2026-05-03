# Saved Searches

← [Back to TDD](../tdd.md)

## 7. Saved Searches

Saved searches are backend-persisted via BFF. They belong to a `SavedSearch` entity with a JSONB `filterState` column.

### 7.1 Data Model (BFF perspective)

```typescript
// Sent to BFF on save
interface CreateSavedSearchDto {
  name: string;
  entityName: string;
  rsqlQuery: string;
  filterState: Record<string, unknown>;
  scope: 'ALL' | 'USER';
}
```

### 7.2 BFF Routes

```
GET    /api/saved-searches?entity=loan       → list for current user + global ones
POST   /api/saved-searches                   → create
DELETE /api/saved-searches/:id               → delete (own only, or ROLE_ADMIN for global)
```

### 7.3 UI Behavior

- Saved searches accessible from two places: the search sidebar (bottom list) and the table toolbar ("Saved Searches ▾"
  dropdown)
- Loading a saved search: restores `filterState` into the sidebar form, compiles to RSQL, fires query
- Scope `ALL` searches shown to all users with read access to that entity
- Scope `USER` searches visible only to the creator
- Delete: only owner or `ROLE_ADMIN` can delete
