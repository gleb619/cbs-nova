import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

// `__tests__/purity.spec.ts` lives at src/__tests__/. Step one directory up so we scan src/.
// Use both fileURLToPath (Node ESM) and import.meta.dirname (Node 20.11+) so the test is
// robust regardless of how vitest/esbuild rewrites import.meta.url.
function resolveHere(): string {
  const metaUrl = (import.meta as { url?: string }).url
  if (metaUrl && (metaUrl.startsWith('file://') || metaUrl.startsWith('file:'))) {
    return dirname(fileURLToPath(metaUrl))
  }
  const metaDirname = (import.meta as { dirname?: string }).dirname
  if (metaDirname) return metaDirname
  throw new Error('Cannot resolve current directory: import.meta.url is not a file URL')
}

const here = resolveHere()
const srcDir = join(here, '..')

// Forbidden Nuxt-only imports/APIs in `components/src/`.
// Source of truth: frontend/AGENTS.md § `components` purity.
const forbiddenPatterns = [
  { name: '$fetch(', pattern: /\$fetch\s*\(/ },
  { name: 'useRouter(', pattern: /\buseRouter\s*\(/ },
  { name: 'useState(', pattern: /\buseState\s*\(/ },
  { name: 'useNuxtApp(', pattern: /\buseNuxtApp\s*\(/ },
  { name: 'useRuntimeConfig(', pattern: /\buseRuntimeConfig\s*\(/ },
  { name: '<NuxtLink', pattern: /<NuxtLink[\s>]/ },
  { name: "from '#app'", pattern: /from ['"]#app['"]/ },
  { name: "from '#imports'", pattern: /from ['"]#imports['"]/ },
  { name: "from 'nuxt/app'", pattern: /from ['"]nuxt\/app['"]/ },
  { name: "from 'nuxt'", pattern: /from ['"]nuxt['"]/ },
] as const

type Violation = {
  file: string
  line: number
  rule: string
  text: string
}

function isTestFile(name: string): boolean {
  return name.includes('.spec.') || name.includes('.test.')
}

function discoverSourceFiles(dir: string): string[] {
  const files: string[] = []

  for (const entry of readdirSync(dir)) {
    if (entry === '__tests__') continue
    if (entry === 'dist') continue
    if (entry === 'node_modules') continue

    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      files.push(...discoverSourceFiles(full))
      continue
    }

    if (!full.endsWith('.ts') && !full.endsWith('.vue')) continue
    if (isTestFile(entry)) continue

    files.push(relative(srcDir, full))
  }

  return files
}

function scanForViolations(files: string[]): Violation[] {
  const violations: Violation[] = []

  for (const relFile of files) {
    const full = join(srcDir, relFile)
    const content = readFileSync(full, 'utf-8')
    const lines = content.split('\n')

    lines.forEach((rawLine, index) => {
      const line = index + 1
      for (const rule of forbiddenPatterns) {
        if (rule.pattern.test(rawLine)) {
          violations.push({
            file: relFile,
            line,
            rule: rule.name,
            text: rawLine.trim(),
          })
        }
      }
    })
  }

  return violations
}

describe('components/src purity', () => {
  it('discovers at least one source file under src/ (sanity check)', () => {
    const files = discoverSourceFiles(srcDir)
    expect(files.length).toBeGreaterThan(0)
  })

  it('has no Nuxt-only imports or APIs in non-test source files', () => {
    const violations = scanForViolations(discoverSourceFiles(srcDir))
    if (violations.length > 0) {
      const lines = violations.map((v) => `  - ${v.file}:${v.line}  ${v.rule}\n      ${v.text}`)
      throw new Error(
        `components/src purity violations (Nuxt-only imports/APIs are not allowed here):\n${lines.join('\n')}\n\n` +
          `See frontend/AGENTS.md § \`components\` purity. Move Nuxt-coupled code to admin-ui-plugin.`,
      )
    }
    expect(violations).toEqual([])
  })
})
