import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

const configDir = fileURLToPath(new URL('.', import.meta.url))
const monacoEditorRoot = resolve(configDir, 'node_modules/monaco-editor/esm/vs')

function resolveMonacoPaths(): import('vite').Plugin {
  const prefix = 'monaco-editor/esm/vs/'
  return {
    name: 'resolve-monaco-subpaths',
    enforce: 'pre',
    resolveId(id) {
      if (id === 'monaco-editor') {
        return resolve(monacoEditorRoot, 'editor/editor.api.js')
      }
      if (id.startsWith(prefix)) {
        return resolve(monacoEditorRoot, id.slice(prefix.length))
      }
      return null
    },
  }
}

export default defineConfig({
  plugins: [vue(), resolveMonacoPaths()],
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
