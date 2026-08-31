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
      // Route 'ofetch' imports to the test shim so the global $fetch stub in
      // vitest.setup.ts is observed by composables that import it explicitly
      // (ofetch >= 1.5 exports its own $fetch and ignores globalThis.$fetch).
      ofetch: r('./vitest.ofetch-stub.ts'),
      // 'nuxt/app' only resolves inside a prepared .nuxt build; stub it so
      // unit-tested composables can import useState and friends.
      'nuxt/app': r('./vitest.nuxt-app-stub.ts'),
      // #components is a Nuxt virtual module; provide a minimal stub for layout specs.
      '#components': r('./vitest.nuxt-components-stub.ts'),
      // vue-router is a transitive dep of nuxt and is not hoisted by pnpm's
      // strict layout. Alias to a stub that captures the navigation-guard
      // handlers the page registers so specs can invoke them directly.
      'vue-router': r('./vitest.vue-router-stub.ts'),
    },
  },
  test: {
    environment: 'happy-dom',
    globals: true,
    passWithNoTests: true,
    setupFiles: ['./vitest.setup.ts'],
  },
})
