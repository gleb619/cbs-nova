/// <reference types="vitest" />

import vue from '@vitejs/plugin-vue';
import tsconfigPaths from 'vite-tsconfig-paths';
import { configDefaults, defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [vue(), tsconfigPaths()],
  test: {
    setupFiles: ['./setupTests.ts'],
    reporters: ['verbose', 'vitest-sonar-reporter'],
    outputFile: {
      'vitest-sonar-reporter': 'build/test-results/TESTS-results-sonar.xml',
    },
    globals: true,
    logHeapUsage: true,
    maxWorkers: 2,
    environment: 'jsdom',
    cache: false,
    include: ['src/unit/**/*.{test,spec}.?(c|m)[jt]s?(x)'],
    coverage: {
      thresholds: {
        perFile: true,
        autoUpdate: true,
        100: true,
      },
      include: ['composables/**/*.ts', 'infrastructure/**/*.ts', 'plugins/**/*.ts'],
      exclude: [...(configDefaults.coverage.exclude as string[])],
      provider: 'istanbul',
      reportsDirectory: 'build/test-results/',
      reporter: ['html', 'json', 'json-summary', 'text', 'text-summary', 'lcov', 'clover'],
      watermarks: {
        statements: [100, 100],
        branches: [100, 100],
        functions: [100, 100],
        lines: [100, 100],
      },
    },
  },
});
