# Frontend Knowledge Base — CBS Nova

This knowledge base covers the architecture, stack, and development patterns of the CBS Nova frontend.

## Document Directory

1. **[Overview & Structure](./frontend/01-overview.md)**
    * Nuxt 3 SPA + Tailwind CSS v4 stack overview.
    * Workspace organization: `frontend/` vs `frontend-plugin/`.

2. **[Architecture & Dependency Injection](./frontend/02-architecture.md)**
    * Hexagonal (Ports and Adapters) architecture rules.
    * DI context management with `piqure`.

3. **[Framework: Plugins & Routing](./frontend/03-framework.md)**
    * Nuxt 3 plugin discovery and initialization flow.
    * Centralized and feature-based routing with auth guards.

4. **[Authentication Flow](./frontend/04-authentication.md)**
    * Local Auth (development) vs Keycloak (production).
    * Token persistence strategy and storage migration.

5. **[Styling, State & i18n](./frontend/05-ux-styling.md)**
    * Tailwind v4 design tokens and CSS import chain.
    * State management with Pinia and i18next registration.

6. **[HTTP Layer & API Proxy](./frontend/06-api-http.md)**
    * Dev proxy configuration and Axios base URL setup.
    * Bearer token injection and runtime environment variables.

7. **[Tooling & Commands Reference](./frontend/07-development.md)**
    * Biome (Lint/Format), Vitest (Unit), and Playwright (E2E) configs.
    * Essential pnpm and Gradle commands for development.

8. **[Adding a New Feature](./frontend/08-adding-features.md)**
    * Step-by-step guide to implementing a new domain feature.

9. **[Maintenance & Troubleshooting](./frontend/09-maintenance.md)**
    * Known issues, TODOs, and common development roadblocks.
    * Browser verification guides and UI screenshots.

---

## Core Stack Summary

- **Framework:** Nuxt 3.15 (SPA mode)
- **Styling:** Tailwind CSS v4
- **Language:** TypeScript
- **Architecture:** Hexagonal (Split Workspace)
- **DI:** piqure
- **Build Tool:** Gradle + pnpm
