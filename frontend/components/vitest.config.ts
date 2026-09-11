import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      'monaco-editor-vue3': fileURLToPath(
        new URL('./node_modules/monaco-editor-vue3/dist/index.mjs', import.meta.url),
      ),
    },
  },
  test: {
    environment: 'happy-dom',
    globals: true,
    setupFiles: ['./vitest.setup.ts'],
    server: {
      deps: {
        inline: [/monaco-editor-vue3/],
      },
    },
  },
})
