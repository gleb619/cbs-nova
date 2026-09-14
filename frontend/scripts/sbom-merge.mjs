#!/usr/bin/env node
// T414: Generate a single CycloneDX SBOM for the pnpm workspace front-end.
//
// `@cyclonedx/cyclonedx-npm` is npm-centric: it shells out to `npm ls` to walk
// the dependency tree. In a pnpm workspace the flat `node_modules/` is shaped
// very differently from npm's expectations, so `npm ls` reports many
// "missing dependency" errors that are harmless for SBOM generation. We
// pass `--ignore-npm-errors` to tolerate them. We also unset `npm_execpath`
// so the tool does not attempt to delegate to pnpm (pnpm 10 dropped the
// `--all/--omit` flags that this tool passes to `pnpm ls`).
//
// The resulting SBOM is written to `../build/sbom/frontend.cdx.json` at the
// worktree root.
import { spawnSync } from 'node:child_process'
import { mkdirSync, existsSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const frontendDir = resolve(__dirname, '..')
const worktreeRoot = resolve(frontendDir, '..')
const outputDir = join(worktreeRoot, 'build', 'sbom')
const outputFile = join(outputDir, 'frontend.cdx.json')

const cyclonedxCli = resolve(
  frontendDir,
  'node_modules',
  '@cyclonedx',
  'cyclonedx-npm',
  'bin',
  'cyclonedx-npm-cli.js',
)

if (!existsSync(cyclonedxCli)) {
  console.error(`sbom-merge: ${cyclonedxCli} not found; run 'pnpm install' first`)
  process.exit(1)
}

mkdirSync(outputDir, { recursive: true })

const env = { ...process.env }
delete env.npm_execpath

console.log('sbom-merge: invoking @cyclonedx/cyclonedx-npm at workspace root')
const r = spawnSync(
  process.execPath,
  [
    cyclonedxCli,
    '--output-format',
    'JSON',
    '--output-file',
    outputFile,
    '--spec-version',
    '1.6',
    // pnpm-managed node_modules is not flat the way npm expects; tolerate
    // `npm ls` errors so we still get a BOM derived from the lockfile.
    '--ignore-npm-errors',
  ],
  { cwd: frontendDir, env, stdio: ['ignore', 'inherit', 'pipe'] },
)

if (r.status !== 0) {
  console.error(`sbom-merge: cyclonedx-npm failed (exit ${r.status})`)
  process.exit(r.status ?? 1)
}

if (!existsSync(outputFile)) {
  console.error(`sbom-merge: expected ${outputFile} to be written, but it is missing`)
  process.exit(1)
}

console.log(`sbom-merge: wrote ${outputFile}`)
