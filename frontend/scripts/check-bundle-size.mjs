import { readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

const NUXT_OUTPUT_DIR = 'admin-ui-plugin/.output/public/_nuxt'
// ~1.5x the post-slim largest JS chunk; tune after re-bundling.
const MAX_CHUNK_BYTES = 2_300_000

function check() {
  const entries = readdirSync(NUXT_OUTPUT_DIR, { withFileTypes: true })
  let maxBytes = 0
  let maxFile = ''
  const failures = []

  for (const entry of entries) {
    if (!entry.isFile()) continue
    const file = entry.name
    if (!file.endsWith('.js')) continue
    const full = join(NUXT_OUTPUT_DIR, file)
    const { size } = statSync(full)
    if (size > maxBytes) {
      maxBytes = size
      maxFile = file
    }
    if (size > MAX_CHUNK_BYTES) {
      failures.push(
        `${file}: ${size.toLocaleString()} bytes > budget ${MAX_CHUNK_BYTES.toLocaleString()}`,
      )
    }
    if (/\.worker-.*\.js$/.test(file)) {
      failures.push(`${file}: worker chunk emitted`)
    }
  }

  if (failures.length > 0) {
    console.error('Bundle size check failed:')
    for (const failure of failures) console.error(`  - ${failure}`)
    process.exit(1)
  }

  console.log(`Bundle OK. Largest JS chunk: ${maxFile} (${maxBytes.toLocaleString()} bytes)`)
}

check()
