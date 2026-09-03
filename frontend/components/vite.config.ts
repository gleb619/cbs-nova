import { resolve } from 'node:path'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue()],
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
