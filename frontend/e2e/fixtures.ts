import { test as base, expect } from '@playwright/test'

// Shared fixtures + gating helpers for the smoke suite.
//
// `backendRequired` skips a spec when E2E_BACKEND is unset (the default in
// offline / CI runs where the Spring backend is not running). Specs that only
// need the Nuxt BFF (which itself just proxies to the backend) and want to
// exercise the dead-backend path should NOT use this fixture.

export const test = base.extend({})

export const backendRequired = test.extend<{ backend: true }>({
  backend: [
    // biome-ignore lint/correctness/noEmptyPattern: Playwright fixture requires destructured-fixture parameter shape.
    async ({}, use) => {
      test.skip(!process.env.E2E_BACKEND, 'set E2E_BACKEND=1 to enable backend-required specs')
      await use(true as const)
    },
    { auto: true },
  ],
})

export { expect }
