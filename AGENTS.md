# cbs-nova — Agent Guide

cbs-nova is a Temporal DSL Orchestration Engine with a Java backend and a Vue/Nuxt admin frontend.

- Backend: declarative Java DSL that compiles to Temporal workflows/activities. Lives in `backend/`.
- Frontend: Nuxt 3 admin UI with a Nitro BFF that proxies to Spring Boot. Lives in `frontend/`.

## Task routing

- If your task is about the Java backend, DSL, code generation, Temporal workers, or Spring Boot API: read
  `backend/AGENTS.md` first.
- If your task is about the Vue/Nuxt UI, Tailwind styling, Pinia stores, or BFF routes: read 
  `frontend/AGENTS.md` first.

## Architecture docs

- `docs/architecture-backend.md` — backend design and runtime modes.
- `docs/architecture-ui.md` — frontend/BFF architecture.
