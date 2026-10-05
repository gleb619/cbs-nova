// Development-only Nuxt config. Used by `nuxt dev` and `nuxt build` to run
// the admin-ui-plugin as a standalone Nuxt application for local development.
// In production the plugin is consumed via `modules: ['@cbs/admin-ui-plugin']`
// inside a host Nuxt app.
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import type { Plugin } from 'vite'
import AdminUiPlugin from './module'

const configDir = fileURLToPath(new URL('.', import.meta.url))
const componentsNodeModules = resolve(configDir, '../components/node_modules')
const monacoEditorRoot = resolve(componentsNodeModules, 'monaco-editor/esm/vs')

function resolveMonacoPaths(): Plugin {
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

export default defineNuxtConfig({
  modules: [AdminUiPlugin],
  devtools: { enabled: true },
  compatibilityDate: '2024-04-03',
  pages: true,
  // Process the @tailwind directives in the plugin's global stylesheet.
  // The Tailwind config at ./tailwind.config.ts already scans both the
  // plugin pages and the shared @cbs/components SFCs.
  postcss: {
    plugins: {
      '@tailwindcss/postcss': {},
      autoprefixer: {},
    },
  },
  vite: {
    plugins: [resolveMonacoPaths()],
  },
})
