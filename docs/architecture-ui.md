# Temporal DSL Orchestration Engine — UI Architecture

This document describes the frontend tier of the cbs-nova project. It sits alongside the backend architecture
documented in `architecture-backend.md` and uses the color system defined in `colors.md`.

## Purpose

The frontend provides an administrative web interface for the orchestration engine. It is a Vue/Nuxt-based layer that
communicates with the existing Spring Boot backend through a small TypeScript backend-for-frontend (BFF) embedded inside
the Nuxt application.

## Workspace layout

The frontend lives in `./frontend` and is managed as a pnpm workspace.

```
frontend/
├── package.json              # workspace root scripts and shared devDependencies
├── pnpm-workspace.yaml       # members: [admin-ui, components]
├── admin-ui/                 # Nuxt 3 application
│   ├── app/
│   │   ├── components/       # admin-ui-specific Vue components
│   │   ├── composables/      # shared Vue composables
│   │   ├── layouts/
│   │   ├── pages/
│   │   └── stores/           # Pinia stores
│   ├── server/               # Nitro TypeScript backend (BFF)
│   │   ├── api/v1/           # proxy routes to Spring Boot
│   │   └── utils/            # JWT helpers, HTTP client, config
│   ├── nuxt.config.ts
│   ├── tailwind.config.ts
│   ├── package.json
│   └── .env.example
└── components/               # reusable Vue component library
    ├── src/
    │   ├── components/       # exported SFCs
    │   ├── composables/
    │   ├── tailwind.config.ts # canonical color theme
    │   └── index.ts          # public exports
    ├── package.json
    └── vite.config.ts
```

## Packages

### `admin-ui`

A Nuxt 3 application that serves:

- the admin web interface (pages, layouts, components),
- shared client-side state via Pinia,
- server-side proxy routes that talk to the Spring Boot API.

The Nuxt `server/` directory is the TypeScript backend of the admin UI. It is responsible for authentication tokens,
request forwarding, response shaping, and error translation.

### `components`

A standalone Vue 3 + Vite library package. It exposes reusable components, composables, and the Tailwind color theme
so that other projects can embed them without depending on the full `admin-ui` application.

## Communication with the backend

```
┌──────────────┐          ┌──────────────────────────┐          ┌──────────────┐
│   Browser    │  HTTP    │   admin-ui (Nuxt)        │  HTTP +  │  Spring Boot │
│  (Vue pages) │ ───────▶ │  ┌─────────────────────┐ │  JWT     │   API        │
│              │          │  │  Nitro server/      │ │────────▶│              │
└──────────────┘          │  │  BFF routes         │ │          └──────────────┘
                          │  └─────────────────────┘ │
                          └──────────────────────────┘
```

- The browser never talks directly to the Spring Boot API.
- Nuxt server routes request or refresh a JWT and forward authenticated calls to Spring Boot.
- The JWT is kept on the server side; the browser only holds its own session cookie with `admin-ui`.

## Styling

All UI styling is based on the brandbook in `docs/colors.md`.

- `components/src/tailwind.config.ts` is the canonical implementation of the palette.
- `admin-ui` imports the Tailwind preset from `@cbs/components` so the theme stays single-sourced.

## Build and run

- Install dependencies: `pnpm install`
- Develop the admin UI: `pnpm --filter admin-ui dev`
- Build the admin UI: `pnpm --filter admin-ui build`
- Build the component library: `pnpm --filter components build`

## Relationship to other docs

- `architecture-backend.md` — describes the Java / Temporal orchestration backend the admin UI consumes.
- `colors.md` — defines the Tailwind color palette used by both `admin-ui` and `components`.

## See also

- `docs/architecture-backend.md` — backend architecture and implementation roadmap
- `docs/colors.md` — admin UI color system
