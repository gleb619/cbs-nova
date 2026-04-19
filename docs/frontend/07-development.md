# Tooling & Commands Reference

## 12. Tools Available

### Linting & Formatting — Biome

| Command         | Description                   |
|-----------------|-------------------------------|
| `pnpm lint`     | Check all files               |
| `pnpm lint:fix` | Auto-fix formatting + imports |

### Unit Testing — Vitest

| Command              | Description        |
|----------------------|--------------------|
| `pnpm test`          | Run all unit tests |
| `pnpm test:coverage` | Run with coverage  |

- **Environment:** jsdom
- **Coverage threshold:** 100% (enforced)

### E2E Testing — Playwright

| Command             | Description        |
|---------------------|--------------------|
| `pnpm e2e`          | Open Playwright UI |
| `pnpm e2e:headless` | Headless mode (CI) |

### Browser DevTools

| Tool          | Access                                                                   |
|---------------|--------------------------------------------------------------------------|
| Nuxt DevTools | Bottom-left button in dev mode, or `http://localhost:3000/__devtools__/` |
| Vue DevTools  | Chrome extension — detects Vue 3 app                                     |

---

## 13. Commands Reference

### Development

| Command                      | Directory   | Description                                   |
|------------------------------|-------------|-----------------------------------------------|
| `pnpm install`               | repo root   | Install all workspace dependencies            |
| `pnpm dev`                   | `frontend/` | Start dev server (Local Auth mode) on `:3000` |
| `pnpm serve`                 | `frontend/` | Start dev server (Keycloak mode)              |
| `./gradlew :backend:bootRun` | repo root   | Spring Boot on `:7070`                        |

### Build

| Command                        | Directory   | Description                    |
|--------------------------------|-------------|--------------------------------|
| `pnpm build`                   | `frontend/` | `vue-tsc` + `nuxt build`       |
| `./gradlew :frontend:assemble` | repo root   | Full frontend build via Gradle |

### Environment Variables

| Variable                 | Default                 | Effect                             |
|--------------------------|-------------------------|------------------------------------|
| `SPRING_BOOT_URL`        | `http://localhost:7070` | Backend URL for axios + devProxy   |
| `NUXT_PUBLIC_LOCAL_AUTH` | `false`                 | Enable Local Auth mode when `true` |
