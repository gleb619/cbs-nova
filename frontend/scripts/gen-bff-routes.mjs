#!/usr/bin/env node
/**
 * T561 — runner for the OpenAPI-driven BFF proxy route generator.
 *
 * Compiles `admin-ui-plugin/scripts/generateBffRoutes.ts` with the workspace
 * `typescript` package into a temp dir and executes it, so the generator works
 * on any Node version supported by the repo (CI uses Node 20, which cannot run
 * TypeScript sources directly).
 *
 *   pnpm gen:bff-routes    → regenerate in place
 *   pnpm check:bff-routes  → regenerate into a temp dir and drift-check
 *
 * Extra args (e.g. `--spec <path>`, `--outdir <dir>`) are passed through —
 * useful to prove the check fails when openapi.json changes shape.
 */
import { createRequire } from 'node:module'
import { execFileSync } from 'node:child_process'
import { existsSync, mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendDir = dirname(dirname(fileURLToPath(import.meta.url)))
const pluginDir = join(frontendDir, 'admin-ui-plugin')
const generatorTs = join(pluginDir, 'scripts', 'generateBffRoutes.ts')
const specPath = join(frontendDir, '..', 'docs', 'openapi.json')
const isCheck = process.argv.includes('--check')

// Fail closed: the BFF route surface is derived from the OpenAPI contract.
if (!existsSync(specPath)) {
  console.error(
    `[gen:bff-routes] ${specPath} not found.\n` +
      'The BFF proxy route surface is generated from docs/openapi.json; restore it and retry.',
  )
  process.exit(1)
}

const require = createRequire(join(frontendDir, 'package.json'))
const tscBin = require.resolve('typescript/bin/tsc')
const buildDir = mkdtempSync(join(tmpdir(), 'bff-routes-'))
execFileSync(
  process.execPath,
  [
    tscBin,
    generatorTs,
    '--outDir',
    buildDir,
    '--module',
    'commonjs',
    '--target',
    'es2022',
    '--skipLibCheck',
    '--esModuleInterop',
  ],
  { stdio: 'inherit' },
)

const passthrough = process.argv.slice(2).filter((a) => a !== '--check')
const args = [join(buildDir, 'generateBffRoutes.js'), '--plugin-dir', pluginDir]
if (isCheck) args.push('--check', '--outdir', join(buildDir, 'out'))
args.push(...passthrough)

try {
  execFileSync(process.execPath, args, { stdio: 'inherit' })
} catch (err) {
  process.exit(err && typeof err === 'object' && 'status' in err ? (err.status ?? 1) : 1)
}
