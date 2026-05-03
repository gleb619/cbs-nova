# Forms & Validation

← [Back to TDD](../tdd.md)

## 8. Forms & Validation

### 8.1 Shared Form Component

One `AdminEntityForm` component handles both create and edit. Mode is passed as a prop. The form renders fields defined
in `EntityRegistration.fields` where `showInForm !== false`.

```vue
<!-- packages/admin-ui/components/Admin/Entity/AdminEntityForm.vue -->
<script setup lang="ts">
const props = defineProps<{
  entity: string;
  recordId?: string;
  mode: 'create' | 'edit';
}>();

const { config } = useAdminEntity(props.entity);
const { form, errors, submit, reset } = useAdminForm(props.entity, props.recordId);
</script>

<template>
  <component :is="config.formMode === 'drawer' ? AdminDrawer : AdminPage">
    <FormSection v-for="section in formSections" :key="section.key">
      <FormField
        v-for="field in section.fields"
        :key="field.key"
        :field="field"
        :model-value="form[field.key]"
        :error="errors[field.key]"
        :disabled="field.readonly || !abac.can('UPDATE', entity)"
        @update:model-value="form[field.key] = $event"
      />
    </FormSection>
    <FormActions :loading="submitting" @submit="submit" @cancel="reset" />
  </component>
</template>
```

### 8.2 Validation — Zod in Nuxt BFF

Validation schemas are defined in Nuxt server routes, not in Vue components. The BFF validates every POST/PUT before
forwarding to Spring.

```typescript
// server/api/loans/index.post.ts
import { z } from 'zod';

const CreateLoanSchema = z.object({
  customerId: z.string().min(1),
  amount: z.number().positive(),
  currencyId: z.number().int(),
  branchId: z.number().int(),
  termMonths: z.number().int().min(1).max(360),
});

export default defineEventHandler(async (event) => {
  const body = await readBody(event);
  const parsed = CreateLoanSchema.safeParse(body);

  if (!parsed.success) {
    throw createError({
      statusCode: 422,
      data: { code: 'VALIDATION_ERROR', details: parsed.error.flatten() },
    });
  }

  return $fetch(`${useRuntimeConfig().springBaseUrl}/loans`, {
    method: 'POST',
    body: parsed.data,
    headers: { Authorization: `Bearer ${event.context.authToken}` },
  });
});
```

Client-side validation (for immediate feedback before submit) uses the same Zod schema exported from a shared location:

```typescript
// packages/admin-core/src/validation/loan.schema.ts
// Schemas can be shared between BFF and client — they live in admin-core
export const CreateLoanSchema = z.object({ ... });
```

### 8.3 Field Components

| FieldType       | Component               | Notes                                                 |
|-----------------|-------------------------|-------------------------------------------------------|
| `string`        | `FieldString.vue`       | text input                                            |
| `number`        | `FieldNumber.vue`       | numeric input with formatting                         |
| `boolean`       | `FieldBoolean.vue`      | toggle switch                                         |
| `date`          | `FieldDate.vue`         | date picker                                           |
| `datetime`      | `FieldDatetime.vue`     | datetime picker                                       |
| `enum`          | `FieldEnum.vue`         | select dropdown, options from config                  |
| `json`          | `FieldJson.vue`         | JSON editor (Monaco lite or textarea with validation) |
| `relation-one`  | `FieldRelationOne.vue`  | combobox + optional modal picker                      |
| `relation-many` | `FieldRelationMany.vue` | multi-select combobox + optional modal picker         |
