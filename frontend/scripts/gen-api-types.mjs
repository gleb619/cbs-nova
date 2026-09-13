#!/usr/bin/env node
/**
 * Generates `components/src/types/api.generated.d.ts` from `docs/openapi.json`
 * using openapi-typescript. Run via `pnpm gen:api-types` from frontend/.
 *
 * `--check` regenerates to a temp file and diffs it against the committed
 * generated file (used by `pnpm check:api-types` / CI drift guard).
 */
import { execFileSync } from 'node:child_process'
import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendDir = dirname(dirname(fileURLToPath(import.meta.url)))
const specPath = join(frontendDir, '..', 'docs', 'openapi.json')
const outPath = join(frontendDir, 'components', 'src', 'types', 'api.generated.d.ts')
const banner =
  '/**\n' +
  ' * GENERATED — do not edit. Regenerate with `pnpm gen:api-types` from frontend/.\n' +
  ' * Source: docs/openapi.json (see docs/plans/T485-frontend-api-types-from-openapi.md).\n' +
  ' */\n'

const check = process.argv.includes('--check')

function generate(targetPath) {
  execFileSync('pnpm', ['exec', 'openapi-typescript', specPath, '-o', targetPath], {
    cwd: frontendDir,
    stdio: 'inherit',
  })
  writeFileSync(targetPath, banner + readFileSync(targetPath, 'utf8'))
}

if (check) {
  const tmpFile = join(mkdtempSync(join(tmpdir(), 'api-types-')), 'api.generated.d.ts')
  generate(tmpFile)
  if (readFileSync(tmpFile, 'utf8') !== readFileSync(outPath, 'utf8')) {
    console.error(
      'api.generated.d.ts is out of date — run `pnpm gen:api-types` and commit the result.',
    )
    process.exit(1)
  }
  console.log('api.generated.d.ts is up to date.')
} else {
  generate(outPath)
  console.log(`wrote ${outPath}`)
}
