# Auth Flow & i18n

← [Back to TDD](../tdd.md)

## 14. Auth Flow

Pure SPA Keycloak-js flow. No server-side session.

```
1. User opens /admin/*
2. auth.global.ts middleware checks Pinia auth store
3. If not authenticated → keycloak.login() → redirects to Keycloak
4. Keycloak redirects back with authorization code
5. keycloak-js exchanges code for tokens (handled by keycloak-js internally)
6. Token stored in memory (Pinia auth store) — never localStorage
7. All BFF calls include token via: $fetch('/api/...', { headers: { 'x-auth-token': token } })
8. Nuxt BFF middleware forwards token to Spring as Bearer header
9. Token refresh handled by keycloak-js (silent refresh via iframe)
```

```typescript
// stores/auth.store.ts
export const useAuthStore = defineStore('auth', () => {
  const keycloak = ref<Keycloak | null>(null);
  const token = ref<string | null>(null);
  const roles = ref<string[]>([]);
  const isAuthenticated = ref(false);

  async function init() {
    const kc = new Keycloak({
      url: useRuntimeConfig().public.keycloakUrl,
      realm: useRuntimeConfig().public.keycloakRealm,
      clientId: useRuntimeConfig().public.keycloakClientId,
    });
    await kc.init({ onLoad: 'check-sso', silentCheckSsoRedirectUri: '/silent-check-sso.html' });
    keycloak.value = kc;
    token.value = kc.token ?? null;
    isAuthenticated.value = kc.authenticated ?? false;
    roles.value = kc.realmAccess?.roles ?? [];

    // Auto-refresh token before expiry
    kc.onTokenExpired = () => kc.updateToken(30);
  }

  async function login() {
    await keycloak.value?.login();
  }

  return { token, roles, isAuthenticated, init, login };
});
```

---

## 15. i18n

i18n is present from day one. English first, structure ready for additional locales.

### 15.1 Setup

```typescript
// nuxt.config.ts (host app)
export default defineNuxtConfig({
  extends: ['../packages/admin-ui'],
  modules: ['@nuxtjs/i18n'],
  i18n: {
    defaultLocale: 'en',
    locales: [{ code: 'en', file: 'en.json' }],
    langDir: './i18n/',
  },
});
```

### 15.2 Translation File Structure

```json
// frontend/app/i18n/en.json
{
  "sidebar": {
    "finance": "Finance",
    "operations": "Operations",
    "orchestration": "Orchestration",
    "system": "System"
  },
  "entities": {
    "loan": {
      "label": "Loans",
      "fields": {
        "id": "ID",
        "customerId": "Customer ID",
        "amount": "Amount",
        "status": "Status",
        "branchId": "Branch",
        "createdAt": "Created At"
      }
    },
    "currency": {
      "label": "Currencies",
      "fields": { ... }
    }
  },
  "workflow": {
    "actions": {
      "APPROVE": "Approve",
      "CANCEL": "Cancel",
      "ROLLBACK": "Rollback",
      "SUBMIT": "Submit",
      "CLOSE": "Close",
      "REJECT": "Reject"
    },
    "states": {
      "ENTERED": "Entered",
      "ACTIVE": "Active",
      "CLOSED": "Closed",
      "CANCELLED": "Cancelled",
      "FAULTED": "Faulted"
    },
    "viewDetails": "View execution details",
    "noExecution": "No active workflow"
  },
  "common": {
    "save": "Save",
    "cancel": "Cancel",
    "delete": "Delete",
    "edit": "Edit",
    "create": "Create",
    "search": "Search",
    "filter": "Filter",
    "loading": "Loading...",
    "noResults": "No results found",
    "confirm": "Are you sure?",
    "savedSearches": "Saved Searches",
    "addCondition": "Add condition",
    "saveSearch": "Save search",
    "applySearch": "Apply",
    "columns": "Columns"
  },
  "errors": {
    "INVALID_TRANSITION": "Invalid workflow transition",
    "MISSING_PARAMETERS": "Missing required parameters",
    "CONTEXT_FAULT": "Context evaluation failed",
    "NOT_FOUND": "Record not found",
    "VALIDATION_ERROR": "Validation failed",
    "FORBIDDEN": "Access denied"
  }
}
```

### 15.3 Codegen produces i18n stubs

When `pnpm generate:module --entity Loan` runs, it appends to `en.json`:

```json
"loan": {
  "label": "Loans",
  "fields": {
    "id": "Id",
    "customerId": "Customer Id",
    "amount": "Amount"
    // ... all fields from OpenAPI schema, label = toLabel(key)
  }
}
```
