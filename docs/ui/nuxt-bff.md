# Nuxt as BFF (Backend-for-Frontend)

← [Back to TDD](../tdd.md)

---

## 3. Nuxt as BFF

Nuxt acts as a Backend-for-Frontend. Vue components **never call Spring Boot directly**. All HTTP goes through Nuxt
server routes.

### 3.1 Responsibilities

| Responsibility          | Owner                  | Notes                                                                    |
|-------------------------|------------------------|--------------------------------------------------------------------------|
| Auth token forwarding   | Nuxt server middleware | Keycloak token from request header forwarded to Spring                   |
| Aggregation             | Nuxt server routes     | One BFF call combines multiple Spring responses                          |
| Response transformation | Nuxt server routes     | Reshape/filter Spring payloads before client receives them               |
| HTTP caching            | Nuxt server routes     | `Cache-Control` headers on lookup responses (currencies, branches, etc.) |
| Zod validation          | Nuxt server routes     | Validate incoming form payloads before forwarding to Spring              |
| Error normalization     | Nuxt server routes     | Spring error formats normalized to a single client-facing shape          |

### 3.2 BFF Route Convention

```
frontend/app/server/api/
├── [entity]/
│   ├── index.get.ts          # GET /api/{entity}?filter=...&page=...&sort=...
│   ├── index.post.ts         # POST /api/{entity}
│   ├── [id].get.ts           # GET /api/{entity}/{id}
│   ├── [id].put.ts           # PUT /api/{entity}/{id}
│   ├── [id].delete.ts        # DELETE /api/{entity}/{id}
│   └── lookup.get.ts         # GET /api/{entity}/lookup?q=... → [{id, title}]
├── workflow-executions/
│   ├── index.get.ts
│   ├── [eventNumber].get.ts
│   └── [eventNumber]/
│       ├── actions.post.ts   # POST trigger action (APPROVE, CANCEL, etc.)
│       └── bpmn.get.ts       # GET static BPMN XML
├── saved-searches/
│   ├── index.get.ts
│   ├── index.post.ts
│   └── [id].delete.ts
└── settings/
    ├── index.get.ts
    └── [key].put.ts
```

### 3.3 BFF Middleware

```typescript
// server/middleware/auth.ts
export default defineEventHandler((event) => {
  const token = getHeader(event, 'x-auth-token');
  if (token) {
    // Forward to Spring on all proxied requests
    event.context.authToken = token;
  }
});
```

### 3.4 Example: Aggregated Loan Page BFF Route

```typescript
// server/api/loans/[id].get.ts
export default defineEventHandler(async (event) => {
  const { id } = getRouterParams(event);
  const token = event.context.authToken;
  const headers = { Authorization: `Bearer ${token}` };
  const base = useRuntimeConfig().springBaseUrl;

  // Parallel fetch — loan + its active workflow execution
  const [loan, workflowExecution] = await Promise.all([
    $fetch(`${base}/loans/${id}`, { headers }),
    $fetch(`${base}/workflow-executions?loanId=${id}&status=ACTIVE`, { headers })
      .then((r: any) => r.content?.[0] ?? null)
      .catch(() => null),
  ]);

  return { loan, workflowExecution };
});
```

### 3.5 Error Shape (normalized)

```typescript
// All BFF routes normalize Spring errors to this shape
export interface BffError {
  code: string;          // 'INVALID_TRANSITION' | 'MISSING_PARAMETERS' | 'NOT_FOUND' | etc.
  message: string;
  details?: Record<string, unknown>;
  status: number;
}
```

### 3.6 HTTP Caching on Lookup Endpoints

```typescript
// server/api/currencies/lookup.get.ts
export default defineEventHandler(async (event) => {
  setHeader(event, 'Cache-Control', 'public, max-age=300'); // 5 min
  const data = await $fetch(`${useRuntimeConfig().springBaseUrl}/currencies/lookup`);
  return data;
});
```
