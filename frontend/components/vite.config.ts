import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

const configDir = fileURLToPath(new URL('.', import.meta.url))
const componentsNodeModules = resolve(configDir, 'node_modules')
const monacoEditorRoot = resolve(componentsNodeModules, 'monaco-editor/esm/vs')

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
  build: {
    lib: {
      entry: resolve(__dirname, 'src/index.ts'),
      name: 'CbsComponents',
      fileName: 'index',
    },
    rollupOptions: {
      external: ['vue', 'monaco-editor'],
      output: { globals: { vue: 'Vue' } },
    },
  },
})
