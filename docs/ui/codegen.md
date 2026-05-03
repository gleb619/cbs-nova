# Code Generation

← [Back to TDD](../tdd.md)

## 16. Code Generation

### 16.1 Trigger

Manual only. Developer runs after a new Spring entity is added:

```bash
pnpm generate:module --entity Loan --api-path /loans --group finance
```

### 16.2 What Gets Generated

| Artifact           | Location                             | Notes                                                              |
|--------------------|--------------------------------------|--------------------------------------------------------------------|
| TS API client      | `app/src/api/generated/`             | From OpenAPI spec via `@hey-api/openapi-ts`                        |
| Entity config stub | `app/admin.config.ts` (appended)     | `EntityRegistration` with all fields                               |
| i18n stubs         | `app/i18n/en.json` (appended)        | All field labels as English placeholders                           |
| List page          | `app/pages/admin/{entity}/index.vue` | Only if `--scaffold` flag; skips if exists                         |
| Form page          | `app/pages/admin/{entity}/[id].vue`  | Only if `--scaffold` flag; skips if exists                         |
| BFF routes         | `app/server/api/{entity}/`           | index.get, index.post, [id].get, [id].put, [id].delete, lookup.get |

### 16.3 OpenAPI Extensions

Spring controllers use vendor extensions to hint codegen behavior:

```java
// x-admin-hidden: true     → showInList: false, showInForm: false
// x-admin-readonly: true   → showInForm: false (shown in list, hidden in form)
// x-admin-relation: Company → override relation entity name
// x-admin-group: finance   → sidebar group assignment
// x-admin-label-field: name → labelField for relation combobox
```

### 16.4 Form Mode Decision (codegen time)

```typescript
// Fields with showInForm !== false
const formFieldCount = fields.filter(f => f.showInForm !== false).length;
const formMode = formFieldCount > 8 ? 'page' : 'drawer';
```

### 16.5 `schema-parser.ts`

```typescript
export async function parseEntityFromOpenApi(entityName: string): Promise<EntityGenerationConfig> {
  const spec = await fetchOpenApiSpec('http://localhost:8080/v3/api-docs');
  const schema = spec.components.schemas[entityName];

  const fields: AdminFieldConfig[] = Object.entries(schema.properties)
    .map(([key, prop]: [string, any]) => ({
      key,
      label: `entities.${entityName.toLowerCase()}.fields.${key}`,
      type: mapOpenApiType(prop),
      filterable: prop.type !== 'object',
      sortable: !['object', 'array'].includes(prop.type),
      showInList: !prop['x-admin-hidden'],
      showInForm: !prop['x-admin-readonly'] && !prop['x-admin-hidden'],
      readonly: prop['x-admin-readonly'] ?? false,
      relation: prop['$ref'] || prop['x-admin-relation']
        ? {
            entity: prop['x-admin-relation'] ?? extractRefName(prop['$ref']),
            labelField: prop['x-admin-label-field'] ?? 'name',
            searchable: true,
            modalPicker: true,
          }
        : undefined,
    }));

  const formMode = fields.filter(f => f.showInForm !== false).length > 8 ? 'page' : 'drawer';

  return {
    name: entityName.toLowerCase(),
    label: `entities.${entityName.toLowerCase()}.label`,
    apiPath: `/${entityName.toLowerCase()}s`,
    fields,
    formMode,
  };
}
```
