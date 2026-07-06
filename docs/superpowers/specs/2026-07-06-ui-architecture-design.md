# Admin UI Architecture Design — 2026-07-06

## Goal

Introduce a dedicated frontend tier for the cbs-nova project while keeping the existing Java / Temporal backend unchanged. The work is split into two documentation changes:

1. Rename `docs/architecture.md` → `docs/architecture-backend.md` and update internal references.
2. Create `docs/architecture-ui.md` that describes the new `./frontend` pnpm workspace.

## Out of scope

- Implementing the actual Nuxt/Vue code (this spec covers architecture only).
- Changing backend behavior beyond adding future BFF endpoints the frontend will consume.
- Visual design — that is already defined in `docs/colors.md` and is referenced here.

---

## Part 1 — Rename backend architecture document

### Action

- `git mv docs/architecture.md docs/architecture-backend.md`
- Update all project-level references to the old path.

### References to update

| File | Change |
|------|--------|
| `docs/loop.md` | Replace `docs/architecture.md` with `docs/architecture-backend.md` |
| `backend/AGENTS.md` | Replace `docs/architecture.md` with `docs/architecture-backend.md` |

### Files intentionally left unchanged

- `.idea/workspace.xml` — JetBrains local IDE state, not project documentation.

### Result

The existing Java / Temporal orchestration architecture keeps its canonical URL but now clearly signals that it describes the backend.

---

## Part 2 — New frontend workspace (`./frontend`)

### Layout

```
./frontend
├── package.json              # pnpm workspace root scripts + shared devDeps
├── pnpm-workspace.yaml       # workspace members: [admin-ui, components]
├── admin-ui/                 # Nuxt 3 application
│   ├── app/
│   │   ├── components/       # app-level components
│   │   ├── composables/      # shared Vue composables
│   │   ├── layouts/
│   │   ├── pages/
│   │   └── stores/           # Pinia stores
│   ├── server/               # Nitro BFF routes (TypeScript backend)
│   │   ├── api/
│   │   │   └── v1/           # proxy routes to Spring Boot
│   │   └── utils/            # jwt helpers, http client, config
│   ├── nuxt.config.ts
│   ├── tailwind.config.ts    # imports colors from @cbs/components
│   ├── package.json
│   └── .env.example
└── components/               # Reusable Vue 3 component library
    ├── src/
    │   ├── components/       # exported SFCs
    │   ├── composables/      # library composables
    │   ├── tailwind.config.ts # canonical color theme from docs/colors.md
    │   └── index.ts          # public exports
    ├── package.json
    └── vite.config.ts        # library build target
```

### Packages

#### `@cbs/admin-ui`

- **Type:** Nuxt 3 application.
- **Role:** Admin interface and BFF (backend-for-frontend) for the Spring Boot orchestration API.
- **Key tech:** Vue 3, Nuxt 3, Tailwind CSS, Pinia, `ofetch`, Nitro server routes.

#### `@cbs/components`

- **Type:** Standalone Vite + Vue 3 library package.
- **Role:** Reusable UI components that can be embedded in `admin-ui` or any other project.
- **Exports:** individual SFCs, the shared Tailwind theme, and CSS entry point.

### BFF / auth flow

1. User authenticates with `admin-ui` via the configured identity provider (or a local login form).
2. `admin-ui` keeps the user session in a secure HTTP-only cookie or session store.
3. For every downstream request to Spring Boot:
   - A Nitro server route reads the service-account secret / user token.
   - It requests (or caches) a JWT from the Spring Boot auth endpoint.
   - It forwards the original request to the Spring Boot REST API with the JWT in the `Authorization` header.
   - The response is streamed back to the browser.

This keeps the service-account secret out of the browser and gives the Nuxt app full control over request shaping, caching, and error translation.

### Relationship to existing backend

- `admin-ui` does **not** replace the Spring Boot API.
- It is a consumer/proxy that adapts the backend API to the needs of the admin interface.
- Communication is HTTP + REST over the internal network, authenticated via JWT.

### Relationship to `docs/colors.md`

- `docs/colors.md` defines the complete color brandbook.
- `components/src/tailwind.config.ts` is the source-of-truth implementation of that palette.
- `admin-ui` imports the Tailwind preset from `@cbs/components` so the visual language is consistent and single-sourced.

---

## Decisions

| Topic | Decision | Rationale |
|-------|----------|-----------|
| Workspace manager | pnpm | Required by the user; monorepo-native hoisting and filtering |
| BFF location | `admin-ui/server/` (Nitro) | Idiomatic Nuxt pattern; “own TS backend” stays inside the Nuxt app |
| Component library | Separate `components/` package | Satisfies embeddability requirement; can be versioned independently |
| Styling | Tailwind CSS with shared theme | Already documented in `docs/colors.md`; reused via the components package |
| State management | Pinia | Standard Vue 3 store; works for both client and SSR contexts |
| HTTP client | `ofetch` | Built into Nuxt / Nitro; supports server and client |

---

## Implementation plan (next step)

1. Execute the rename and reference updates in `docs/` and `backend/AGENTS.md`.
2. Create `docs/architecture-ui.md` summarizing this design in project documentation form (not a formal spec).
3. Hand off to the `writing-plans` skill to produce the detailed implementation tasks for scaffolding `./frontend`.
