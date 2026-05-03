# cbs-nova

CBS-Nova: Business Orchestration Engine for core banking operations. Built on Java 25, Spring Boot, and Temporal workflows, with PostgreSQL persistence, Kotlin Script rules engine, and a Vue/Nuxt.js admin UI.

## Screenshots

![index-page.png](docs/screenshots/index-page.png)

## Development Setup

### Prerequisites

- **Java 25** (via sdkman: `sdk install java 25.0.2-tem`)
- **Node.js >= 24**
- **pnpm** (`corepack enable && corepack prepare pnpm@latest --activate`)
- **PostgreSQL** (via docker-compose)

### Architecture

The application runs as three separate services during development:

| Service               | Port    | Description                                        |
|-----------------------|---------|----------------------------------------------------|
| Backend (Spring Boot) | `:7070` | Java API server + Temporal workflows               |
| Nuxt.js BFF           | `:3000` | Backend-for-Frontend, proxies `/api/**` to backend |
| Vite Admin UI         | `:9000` | Vue 3 SPA, proxies `/api/**` to Nuxt BFF           |

Request flow: `Browser → Vite (9000) → Nuxt BFF (3000) → Backend (7070)`

### Quick Start

```bash
# 1. Start infrastructure
docker-compose up -d

# 2. Start backend
./gradlew :backend:bootRun

# 3. Start frontend (in a new terminal)
cd frontend && pnpm install
cd frontend && pnpm run dev:all   # Starts Vite (9000) + Nuxt (3000) in parallel

# Or start individually:
cd frontend && pnpm dev           # Vite only on :9000
cd frontend && pnpm dev:nuxt      # Nuxt only on :3000
```

### Build

```bash
# Full Gradle build (backend + frontend)
./gradlew assemble

# Frontend only
./gradlew :frontend:assemble

# Lint check
./gradlew :frontend:lint
```

### Linting & Formatting

The project uses [Biome](https://biomejs.dev/) for linting and formatting (replaced ESLint + Prettier).

```bash
cd frontend && pnpm run lint       # Check
cd frontend && pnpm run lint:fix   # Auto-fix
```
