# Admin UI — Overview & Goals

← [Back to TDD](../tdd.md)

**Version:** 1.0
**Status:** For team review
**Stack:** Vue 3, Nuxt 3 (BFF), Tailwind CSS, Pinia, Keycloak-js, Zod, TanStack Query
**Date:** 2025

---

## 1. Overview & Goals

### 1.1 Context

The admin panel is the primary operational interface for the CBS Orchestration Platform. It serves two distinct
audiences:

- **Business analysts / operators** — manage business entities (loans, branches, calendars, currencies, dictionaries,
  settings), trigger workflow events, monitor execution state
- **Developers / ops** — inspect execution details, read transition logs, access BPMN diagrams, open DSL editor

The system is hybrid: a large class of entities (dictionaries, currencies, branches, calendars, loans, etc.) is served
by a **generated generic CRUD layer**. A smaller class of domain-specific views (workflow executions, settings, complex
dictionaries) is **handmade** and registered via a configurable router.

### 1.2 Goals

- **Generic CRUD first** — generated entities get a full-featured datatable + drawer/page form at zero hand-written cost
- **Handmade escape hatch** — any entity can replace or extend the generic view via configurable router
- **Nuxt as BFF** — all Spring Boot communication goes through Nuxt server routes; Vue never calls Spring directly
- **ABAC everywhere** — sidebar items, entity actions, table columns, and form fields all respect Keycloak token claims
- **i18n from day one** — English first, translation key stubs generated automatically
- **Simple auth** — pure SPA Keycloak-js flow, token in memory

### 1.3 Non-Goals (v1)

- Rich text editor (TipTap) — Phase 4
- Media upload (S3/MinIO) — Phase 4
- Dark mode — Phase 3
- Mobile responsiveness — Phase 3
- Stakeholder funnel/heatmap UI — v2 (data model ready in backend)
- Dynamic BPMN instance heatmap — v2
- Temporal UI iframe embed — v2 (link only in v1)
- Backend-driven JSON schema for forms
- In-memory BFF caching (HTTP caching headers only)

---

## 2. Repository & Monorepo Structure

```
repo-root/
├── backend/
│   ├── app/
│   ├── dsl-api/
│   ├── dsl-runtime/
│   ├── dsl-compiler/
│   ├── temporal-core/
│   ├── bpmn-export/
│   └── ...
│
├── frontend/
│   ├── packages/
│   │   ├── admin-core/           # 🔴 Runtime engine: composables, stores, services, types
│   │   ├── admin-ui/             # 🟢 Nuxt layer: generic pages, layouts, components
│   │   ├── admin-components/     # 🔵 Tailwind component library (pure presentation)
│   │   └── admin-codegen/        # ⚙️  Hygen templates + OpenAPI gen scripts
│   └── app/                      # 🏠 Host Nuxt app (BFF + project-specific pages)
│       ├── nuxt.config.ts
│       ├── server/               # Nuxt server routes (BFF layer)
│       │   ├── api/
│       │   └── middleware/
│       ├── pages/
│       │   └── admin/
│       ├── components/
│       ├── admin.config.ts       # Entity registrations
│       └── i18n/
│           └── en.json
│
├── _templates/                   # Hygen templates (repo-wide)
│   └── admin-module/
├── package.json                  # PNPM workspace root
└── pnpm-workspace.yaml
```

### `pnpm-workspace.yaml`

```yaml
packages:
  - 'frontend/packages/*'
  - 'frontend/app'
```

### Host App Structure

The `frontend/app/` host Nuxt application is the deployment unit. It:

- Registers all entity configurations in `admin.config.ts`
- Provides the BFF server routes under `server/api/`
- Hosts project-specific handmade pages under `pages/admin/`
- Contains locale files under `i18n/`

The `admin-ui` Nuxt layer provides generic pages and layouts; the host app only creates pages when overriding or
extending the generic behavior.
