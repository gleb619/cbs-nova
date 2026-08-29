/**
 * Test-only ofetch shim, wired in via the `ofetch` alias in vitest.config.ts.
 *
 * vitest.setup.ts installs a `vi.fn()` `$fetch` on globalThis; app composables
 * import `{ $fetch } from 'ofetch'` explicitly. ofetch >= 1.5 exports its own
 * `$fetch` instance and never consults `globalThis.$fetch`, so the explicit
 * import bypassed the global stub and specs hit happy-dom's real fetch. This
 * shim re-exports the real module surface but substitutes `$fetch` with the
 * global stub, restoring the intended "one stub observed everywhere" setup.
 */
import * as real from './node_modules/ofetch/dist/index.mjs'

const g = globalThis as unknown as { $fetch?: unknown }

export const $fetch = (g.$fetch ?? real.$fetch) as typeof real.$fetch
export const ofetch = real.ofetch
export const FetchError = real.FetchError
export const createFetchError = real.createFetchError
