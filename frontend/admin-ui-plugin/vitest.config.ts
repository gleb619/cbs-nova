import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

const r = (p: string) => fileURLToPath(new URL(p, import.meta.url))

export default defineConfig({
  plugins: [vue()],
  resolve: {
    // Map the `~/` alias used by Nitro server routes (and the Nuxt app) to
    // the package root, matching what Nuxt's `~/` resolves to at runtime.
    alias: {
      '~': r('.'),
      // Use the same ofetch entry that the dev server/Nitro runtime resolves.
      // This keeps import identity consistent between source and tests so the
      // global $fetch stub in vitest.setup.ts is observed by httpClient.ts.
      ofetch: r('./node_modules/ofetch/dist/index.mjs'),
    },
  },
  test: {
    environment: 'happy-dom',
    globals: true,
    passWithNoTests: true,
    setupFiles: ['./vitest.setup.ts'],
  },
})
