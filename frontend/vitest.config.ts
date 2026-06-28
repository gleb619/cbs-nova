/// <reference types="vitest" />

import { fileURLToPath } from 'node:url';
import vue from '@vitejs/plugin-vue';
import tsconfigPaths from 'vite-tsconfig-paths';
import { configDefaults, defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [vue(), tsconfigPaths()],
  resolve: {
    alias: {
      'bpmn-js/lib/NavigatedViewer': fileURLToPath(new URL('./src/unit/shared/mocks/bpmn-viewer.mock.ts', import.meta.url)),
    },
  },
  test: {
    setupFiles: ['./setupTests.ts'],
    reporters: ['verbose', 'vitest-sonar-reporter'],
    outputFile: { 'vitest-sonar-reporter': 'build/test-results/TESTS-results-sonar.xml' },
    globals: true,
    logHeapUsage: true,
    maxWorkers: 2,
    environment: 'jsdom',
    cache: false,
    include: ['src/unit/**/*.{test,spec}.?(c|m)[jt]s?(x)'],
    coverage: {
      thresholds: { perFile: true, autoUpdate: true, 100: true },
      include: ['src/**/*.ts?(x)'],
      exclude: [...(configDefaults.coverage.exclude as string[]), 'src/app/router.ts', 'src/app/main.ts', 'src/**/*.component.ts'],
      provider: 'istanbul',
      reportsDirectory: 'build/test-results/',
      reporter: ['html', 'json', 'json-summary', 'text', 'text-summary', 'lcov', 'clover'],
      watermarks: { statements: [100, 100], branches: [100, 100], functions: [100, 100], lines: [100, 100] },
    },
  },
});
