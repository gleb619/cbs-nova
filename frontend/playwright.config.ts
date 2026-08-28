import { defineConfig, devices } from '@playwright/test'

// Playwright config for the admin-ui-plugin. Boots the Nuxt dev server (which
// embeds the Nitro BFF) and runs a small set of smoke specs that exercise the
// highest-value user paths.
//
// Backend-optional gating:
//   - By default we assume the Spring backend is NOT running and point the
//     Nitro BFF at a dead port (127.0.0.1:1) so smoke specs exercise the
//     "what does the UI look like when the backend is unreachable?" path.
//   - Specs that genuinely need a live backend (e.g. Runner happy path) call
//     test.skip(!process.env.E2E_BACKEND, ...) so they self-skip when offline.
//   - To run with a real backend, set E2E_BACKEND_URL (e.g.
//     `E2E_BACKEND_URL=http://localhost:8090 pnpm test:e2e`). The webServer
//     command will propagate it to BACKEND_BASE_URL so the BFF proxies to it.

const backendBaseUrl = process.env.E2E_BACKEND_URL ?? 'http://127.0.0.1:1'
const port = Number(process.env.E2E_PORT ?? 4567)

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: true,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: `http://localhost:${port}`,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: `BACKEND_BASE_URL=${backendBaseUrl} pnpm --filter @cbs/admin-ui-plugin dev --port ${port}`,
    url: `http://localhost:${port}`,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    stdout: 'pipe',
    stderr: 'pipe',
  },
})
