import { createRequire } from 'node:module'
import { dirname, resolve } from 'node:path'
import type { Plugin } from 'vite'

const _require = createRequire(import.meta.url)
const componentsPackageDir = dirname(dirname(dirname(dirname(_require.resolve('@cbs/components/tailwind.config')))))
const monacoEditorRoot = resolve(componentsPackageDir, 'monaco-editor/esm/vs')

function resolveMonacoPaths(): Plugin {
  const prefix = 'monaco-editor/esm/vs/'
  return {
    name: 'cbs-resolve-monaco-subpaths',
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
  compatibilityDate: '2024-09-01',
  future: { compatibilityVersion: 4 },
  devtools: { enabled: true },
  pages: true,

  modules: ['@cbs/admin-ui-plugin'],

  adminUiPlugin: {
    routePrefix: '/nova-admin',
    appName: 'CBS Nova — Operator Portal',
    backendBaseUrl: process.env.BACKEND_BASE_URL || 'http://localhost:8090',
    backendApiKey: process.env.BACKEND_API_KEY,
  },

  runtimeConfig: {
    public: {
      portalName: 'CBS Operator Portal',
    },
  },

  postcss: {
    plugins: {
      '@tailwindcss/postcss': {},
      autoprefixer: {},
    },
  },

  vite: {
    plugins: [resolveMonacoPaths()],
  },

  build: {
    transpile: ['@cbs/admin-ui-plugin'],
  },

  nitro: {
    transpile: ['@cbs/admin-ui-plugin'],
  },
})
