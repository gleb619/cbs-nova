# Datatable & Filters

← [Back to TDD](../tdd.md)

---

## 6. Datatable & Filters

### 6.1 Two-Tier Filter System

The datatable has two mutually exclusive filter modes. Activating the search sidebar **replaces** the quick filter row —
they do not coexist. Both compile to RSQL and go to the BFF.

```
┌──────────────────────────────────────────────────────────────────┐
│  [Search Sidebar btn]  [Saved Searches ▾]   [Column Visibility]  │
├──────────────────────────────────────────────────────────────────┤
│  QUICK FILTER ROW (shown when sidebar is closed)                 │
│  [Name ___________] [Status ▾] [Branch ▾] [Amount from/to]      │
├──────────────────────────────────────────────────────────────────┤
│  # │ Name         │ Status  │ Branch    │ Amount   │ Actions     │
│────┼──────────────┼─────────┼───────────┼──────────┼────────────│
│  1 │ Loan #9981   │ ACTIVE  │ Almaty    │ 500,000  │ ✏️  🗑️      │
│  2 │ ...          │ ...     │ ...       │ ...      │             │
├──────────────────────────────────────────────────────────────────┤
│  ← 1 2 3 ... 12 →                          Showing 1–20 of 234  │
└──────────────────────────────────────────────────────────────────┘
```

When the search sidebar is open, the quick filter row is replaced by a "Search active" indicator chip showing the search
name (if a saved search is loaded) or "Custom search".

### 6.2 Quick Filter

- Rendered above table headers, one input per filterable column
- Input type driven by `FieldType`: string → text, number → range, date → date range, enum → multiselect dropdown,
  boolean → toggle, relation → combobox lookup
- Debounced (300ms) — fires RSQL request on change
- Filter state stored in component-local reactive state (not persisted)
- Cleared when search sidebar is opened

### 6.3 Search Sidebar

A drawer from the **left side** of the screen. Opens over the table (does not push content).

```
┌────────────────────────────┐
│  🔍 Search                 │
│  ─────────────────────     │
│  [+ Add condition]         │
│                            │
│  ┌──────────────────────┐  │
│  │ Name  contains  [__] │  │
│  └──────────────────────┘  │
│         AND ▾              │
│  ┌──────────────────────┐  │
│  │ Status  in  [▾ ___]  │  │
│  └──────────────────────┘  │
│         AND ▾              │  ← AND/OR toggle per join
│  ┌──────────────────────┐  │
│  │ Amount  between      │  │
│  │ [____]   [____]      │  │
│  └──────────────────────┘  │
│                            │
│  [Save Search]  [Apply]    │
│  ─────────────────────     │
│  Saved searches:           │
│  • Active Almaty loans     │
│  • High value pending      │
└────────────────────────────┘
```

- Each condition row: field selector → operator selector → value input
- Operator options driven by field type (string: contains/equals/starts with; number: =/>/</>=/between; date:
  on/before/after/between; enum: in/not in; relation: is/is not)
- Join between conditions: AND / OR toggle per gap
- "Save Search" opens a small modal: name input + scope selector (All users / Only me)
- Saved searches listed at the bottom; click to load state into sidebar form
- Per-user sidebar configuration (which fields appear by default) stored in localStorage per entity

### 6.4 RSQL Builder

```typescript
// services/rsql.builder.ts
export class RsqlBuilder {
  private conditions: RsqlCondition[] = [];
  private joins: ('AND' | 'OR')[] = [];

  add(condition: RsqlCondition, join: 'AND' | 'OR' = 'AND'): this {
    this.conditions.push(condition);
    if (this.conditions.length > 1) this.joins.push(join);
    return this;
  }

  build(): string {
    return this.conditions
      .map((c, i) => {
        const expr = this.buildCondition(c);
        if (i === 0) return expr;
        const join = this.joins[i - 1] === 'AND' ? ';' : ',';
        return `${join}${expr}`;
      })
      .join('');
  }

  private buildCondition(c: RsqlCondition): string {
    switch (c.operator) {
      case 'contains':   return `${c.field}=ilike=*${c.value}*`;
      case 'equals':     return `${c.field}==${c.value}`;
      case 'startsWith': return `${c.field}=ilike=${c.value}*`;
      case 'gt':         return `${c.field}=gt=${c.value}`;
      case 'lt':         return `${c.field}=lt=${c.value}`;
      case 'between':    return `${c.field}=bt=(${c.value[0]},${c.value[1]})`;
      case 'in':         return `${c.field}=in=(${(c.value as string[]).join(',')})`;
      case 'notIn':      return `${c.field}=out=(${(c.value as string[]).join(',')})`;
    }
  }
}
```

### 6.5 Column Visibility

- Default visible columns: `showInList: true` fields in `EntityRegistration`
- User can toggle columns via a popover (column visibility button, top-right of table)
- Preferences stored in `localStorage` key: `col-visibility:{entityName}:{userId}`
- On load: merge stored prefs with current config (new fields added to config default to visible)

### 6.6 Pagination

- Page size options: 10, 20, 50 (default: 20)
- Standard page navigation: first / prev / numbered pages / next / last
- Total count displayed: "Showing 1–20 of 234"
- Page state stored in URL query params (`?page=2&size=20`) for shareable links
