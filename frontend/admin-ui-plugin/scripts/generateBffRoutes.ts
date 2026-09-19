/**
 * T561 — OpenAPI-driven BFF proxy route generator.
 *
 * Reads `docs/openapi.json` (the same contract that feeds
 * `components/src/types/api.generated.d.ts`) and emits:
 *
 *   1. `server/api/v1/generated/manifest.json` — typed route manifest
 *      ({ method, backendPath, bffPath, params } per operationId) consumed by
 *      `module.ts` to mount the generated Nitro stubs at their real `/api/v1/*`
 *      paths and to keep Nitro's file scanner away from the generated dir.
 *   2. `server/api/v1/generated/routes/**` — thin Nitro route stubs that
 *      delegate to `server/utils/proxyFromManifest.ts`.
 *   3. `app/composables/generated/useBffApi.ts` — typed runtime client with one
 *      function per operationId, replacing hand-written `$fetch` calls.
 *
 * Routes whose current BFF implementation does more than plain proxying
 * (auth, SSE events, CSV export, query-param rewrites, header tweaks,
 * catch-all paths, legacy path shapes) are listed in `EXCLUDED_OPERATIONS`
 * and stay explicit hand-written files under `server/api/v1/`.
 *
 * Usage (usually via `pnpm gen:bff-routes` / `pnpm check:bff-routes`):
 *   node generateBffRoutes.js [--spec <openapi.json>] [--plugin-dir <dir>]
 *                             [--outdir <dir>] [--check]
 *
 * `--check` regenerates into `--outdir` (a temp dir) and fails when the output
 * differs from the in-tree generated files or when the hand-written wiring that
 * consumes the manifest has drifted (missing excluded routes, unexpected route
 * files shadowing generated paths, unaccounted OpenAPI operations).
 *
 * The script is plain Node-compatible TypeScript (no external deps); the
 * `frontend/scripts/gen-bff-routes.mjs` wrapper compiles it with `tsc` before
 * running so it works on the CI Node version.
 */

import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { dirname, join, relative } from 'node:path'

// ---------------------------------------------------------------------------
// Configuration — the single place where generation policy lives.
// ---------------------------------------------------------------------------

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE'

interface OpenApiOperation {
  operationId?: string
  parameters?: Array<{ name: string; in: string }>
  requestBody?: unknown
}

type OpenApiDoc = {
  paths: Record<string, Record<string, OpenApiOperation>>
}

export interface BffRouteSpec {
  operationId: string
  method: Method
  /** Backend (Spring Boot) path; may contain `{param}` placeholders. */
  backendPath: string
  /** Public BFF path the stub is mounted at; may contain `{param}` placeholders. */
  bffPath: string
  /** Path parameter names, in order of appearance. */
  params: string[]
}

interface RouteManifest {
  source: string
  routes: Array<BffRouteSpec & { handler: string }>
}

/** Backend path prefix → BFF prefix. Anything else is rejected loudly. */
const BFF_PREFIX: Array<[RegExp, string]> = [
  [/^\/api\/dsl\//, '/api/v1/dsl/'],
  [/^\/api\/executions\//, '/api/v1/executions/'],
  [/^\/api\/executions$/, '/api/v1/executions'],
]

/**
 * Operations that must NOT be turned into generated plain-proxy stubs because
 * their current BFF route does more than plain proxying. Each entry records
 * why, and which committed hand-written route file implements it — the
 * `--check` mode verifies that file still exists.
 */
const EXCLUDED_OPERATIONS: Record<string, { reason: string; handRoute: string }> = {
  createApiKey: {
    reason: 'sets cache-control: no-store on the plaintext-key response',
    handRoute: 'dsl/auth/keys/index.post.ts',
  },
  exportDefinitions: {
    reason: 'forwards the inbound query string (include=drafts)',
    handRoute: 'dsl/definitions/export.get.ts',
  },
  importDefinitions: {
    reason: 'forwards the inbound query string (dryRun)',
    handRoute: 'dsl/definitions/import.post.ts',
  },
  runDefinitionTests: {
    reason: 'rewrites repeated `case` query params into an array',
    handRoute: 'dsl/definitions/[name]/tests/run.post.ts',
  },
  deleteDraft: {
    reason: 'legacy BFF path shape: DELETE is mounted at /drafts/{name}/delete',
    handRoute: 'dsl/drafts/[name]/delete.delete.ts',
  },
  readDslFile: {
    reason: 'catch-all [...path] route (multi-segment file paths)',
    handRoute: 'dsl/files/[...path].get.ts',
  },
  writeDslFile: {
    reason: 'catch-all [...path] route (multi-segment file paths)',
    handRoute: 'dsl/files/[...path].post.ts',
  },
  searchObjects: {
    reason: 'trims filters and omits blank ones from the query',
    handRoute: 'dsl/objects/search.get.ts',
  },
  listAuditEntries: {
    reason: 'query-param allowlist (offset/limit/action)',
    handRoute: 'dsl/audit.get.ts',
  },
  listCompileDiagnostics: {
    reason: 'query-param allowlist (offset/limit/definition)',
    handRoute: 'dsl/diagnostics.get.ts',
  },
  listDomainEvents: {
    reason: 'query-param allowlist (offset/limit/type/aggregate*/correlationId/since)',
    handRoute: 'dsl/events.get.ts',
  },
  getProcessDiagram: {
    reason: 'forwards a trimmed `format` query param',
    handRoute: 'dsl/processes/[name]/diagram.get.ts',
  },
  getConstructSchema: {
    reason: 'rewrites the `mode` query param into the backend path suffix',
    handRoute: 'dsl/schemas/[name].get.ts',
  },
  listExecutions: {
    reason: 'query-param allowlist and entityName→processName rewrite',
    handRoute: 'executions/index.get.ts',
  },
  exportExecutionsCsv: {
    reason: 'raw CSV passthrough with forwarded download headers',
    handRoute: 'executions/export.get.ts',
  },
  events: {
    reason: 'SSE stream via h3 proxyRequest (long-lived, no JSON envelope)',
    handRoute: 'executions/[id]/events.get.ts',
  },
}

/**
 * BFF path overrides for operations whose public BFF path does not follow the
 * prefix rule (only `deleteDraft` today).
 */
const BFF_PATH_OVERRIDES: Record<string, string> = {
  deleteDraft: '/api/v1/dsl/drafts/{name}/delete',
}

/**
 * Operations whose stub forwards the request body via readBody(). springdoc
 * does not document request bodies, so this is maintained explicitly.
 */
const BODY_OPERATIONS = new Set([
  'saveDraft',
  'publishDraft',
  'explainDsl',
  'previewDsl',
  'runDsl',
  'replaceDefinitionTests',
  'writeDslFileByName',
  'bulkWriteDslFiles',
  'createSchedule',
  'pauseSchedule',
  'resumeSchedule',
])

/**
 * GET operations whose legacy stub passed `{ method: 'GET' }` explicitly
 * (kept so the generated call shape matches the behavior asserted by the
 * existing route unit tests).
 */
const EXPLICIT_METHOD_GET_OPERATIONS = new Set(['readPublishHistoryEntry', 'diffPublishHistoryEntry'])

// ---------------------------------------------------------------------------
// Path helpers
// ---------------------------------------------------------------------------

function toBffPath(backendPath: string): string {
  for (const [pattern, prefix] of BFF_PREFIX) {
    if (pattern.test(backendPath)) {
      return backendPath.replace(pattern, prefix)
    }
  }
  throw new Error(`[gen:bff-routes] no BFF prefix mapping for backend path ${backendPath}`)
}

function extractParams(path: string): string[] {
  return [...path.matchAll(/\{(?<name>[^}]+)\}/g)].map((m) => m.groups?.name ?? '')
}

/** `/api/v1/dsl/definitions` + GET → `routes/dsl/definitions.get.ts` */
function handlerRelPath(spec: BffRouteSpec): string {
  const suffix = spec.bffPath.replace(/^\/api\/v1\//, '')
  const segments = suffix.split('/').map((s) => (s.startsWith('{') ? `[${s.slice(1, -1)}]` : s))
  return join('routes', ...segments) + `.${spec.method.toLowerCase()}.ts`
}

// ---------------------------------------------------------------------------
// Generation
// ---------------------------------------------------------------------------

function banner(): string {
  return (
    '/**\n' +
    ' * GENERATED — do not edit. Regenerate with `pnpm gen:bff-routes` from frontend/.\n' +
    ' * Source: docs/openapi.json (T561 — see docs/plans/T561-openapi-driven-bff-proxy-manifest.md).\n' +
    ' */\n'
  )
}

function buildOperations(specPath: string): Array<{ spec: BffRouteSpec; method: Method }> {
  if (!existsSync(specPath)) {
    throw new Error(
      `[gen:bff-routes] ${specPath} not found — the BFF route surface is derived from the OpenAPI ` +
        'contract and the build fails closed without it.',
    )
  }
  const doc = JSON.parse(readFileSync(specPath, 'utf8')) as OpenApiDoc
  if (!doc.paths || typeof doc.paths !== 'object') {
    throw new Error(`[gen:bff-routes] ${specPath} has no "paths" object — not a valid OpenAPI document`)
  }

  const operations: Array<{ spec: BffRouteSpec; method: Method }> = []
  const seen = new Set<string>()
  for (const [backendPath, methods] of Object.entries(doc.paths)) {
    for (const [rawMethod, op] of Object.entries(methods)) {
      const method = rawMethod.toUpperCase() as Method
      if (!['GET', 'POST', 'PUT', 'DELETE'].includes(method)) continue
      const operationId = op.operationId
      if (!operationId) {
        throw new Error(`[gen:bff-routes] ${method} ${backendPath} has no operationId`)
      }
      if (seen.has(operationId)) {
        throw new Error(`[gen:bff-routes] duplicate operationId ${operationId}`)
      }
      seen.add(operationId)
      const bffPath = BFF_PATH_OVERRIDES[operationId] ?? toBffPath(backendPath)
      operations.push({
        method,
        spec: {
          operationId,
          method,
          backendPath,
          bffPath,
          params: extractParams(bffPath),
        },
      })
    }
  }
  operations.sort((a, b) => a.spec.operationId.localeCompare(b.spec.operationId))
  return operations
}

function emitStub(spec: BffRouteSpec): string {
  const hasBody = BODY_OPERATIONS.has(spec.operationId)
  const explicitMethod = spec.method !== 'GET' || EXPLICIT_METHOD_GET_OPERATIONS.has(spec.operationId)
  const literal = JSON.stringify({ ...spec, hasBody, explicitMethod }, null, 2)
  return (
    banner() +
    "import { defineEventHandler } from 'h3'\n" +
    "import { proxyFromManifest } from '~/server/utils/proxyFromManifest'\n" +
    '\n' +
    'export default defineEventHandler((event) =>\n' +
    `  proxyFromManifest(event, ${literal.replace(/\n/g, '\n  ')}),\n` +
    ')\n'
  )
}

function emitClient(operations: Array<{ spec: BffRouteSpec; method: Method }>): string {
  const lines: string[] = [
    banner(),
    "import { $fetch } from 'ofetch'",
    "import type { operations } from '@cbs/components/types'",
    '',
    '/** Per-request overrides accepted by every generated client function. */',
    'export interface BffRequestInit {',
    '  query?: Record<string, unknown>',
    '  body?: unknown',
    '  headers?: Record<string, string>',
    '}',
    '',
    '/** Body of the first 200 response declared for an operation. */',
    'type ExtractBody<R> = R extends { content: infer C }',
    "      ? C extends { 'application/json': infer B }",
    '        ? B',
    "      : C extends { '*/*': infer B }",
    '        ? B',
    '        : unknown',
    '      : unknown',
    'type BffResponse<Op extends keyof operations> = operations[Op] extends {',
    '  responses: { 200: infer R }',
    '}',
    '  ? ExtractBody<R>',
    '  : unknown',
    '',
    'function bffFetch(url: string, method: string, init: BffRequestInit): Promise<unknown> {',
    '  const options: Record<string, unknown> = {}',
    "  if (method !== 'GET') options.method = method",
    '  if (init.query !== undefined) options.query = init.query',
    '  if (init.body !== undefined) options.body = init.body',
    '  if (init.headers !== undefined) options.headers = init.headers',
    '  return Object.keys(options).length > 0',
    '    ? $fetch(url, options as never)',
    '    : $fetch(url)',
    '}',
    '',
  ]
  for (const { spec } of operations) {
    const args = spec.params.map((p) => `${p}: string`)
    const argList = [...args, 'init: BffRequestInit = {}'].join(', ')
    const urlTemplate = spec.bffPath.replace(/\{(?<name>[^}]+)\}/g, '${$<name>}')
    lines.push(
      `export async function ${spec.operationId}(${argList}): Promise<BffResponse<'${spec.operationId}'>> {`,
      `  return bffFetch(\`${urlTemplate}\`, '${spec.method}', init) as Promise<BffResponse<'${spec.operationId}'>>`,
      '}',
      '',
    )
  }
  return lines.join('\n')
}

function writeFileEnsured(path: string, content: string): void {
  mkdirSync(dirname(path), { recursive: true })
  writeFileSync(path, content)
}

function listFilesRecursive(dir: string, base = dir): string[] {
  if (!existsSync(dir)) return []
  const out: string[] = []
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) out.push(...listFilesRecursive(full, base))
    else out.push(relative(base, full))
  }
  return out.sort()
}

export interface GenerateResult {
  manifest: RouteManifest
  generatedFiles: string[]
}

export function generate(specPath: string, pluginDir: string, outDir: string): GenerateResult {
  const operations = buildOperations(specPath)
  const generated = operations.filter(({ spec }) => !EXCLUDED_OPERATIONS[spec.operationId])
  const excluded = operations.filter(({ spec }) => EXCLUDED_OPERATIONS[spec.operationId])

  const manifest: RouteManifest = {
    source: 'docs/openapi.json',
    routes: generated.map(({ spec }) => ({ ...spec, handler: handlerRelPath(spec) })),
  }

  const routesDir = join(outDir, 'server/api/v1/generated')
  writeFileEnsured(join(routesDir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n')

  const generatedFiles: string[] = ['server/api/v1/generated/manifest.json']
  for (const { spec } of generated) {
    const rel = `server/api/v1/generated/${handlerRelPath(spec)}`
    writeFileEnsured(join(outDir, rel), emitStub(spec))
    generatedFiles.push(rel)
  }

  const clientRel = 'app/composables/generated/useBffApi.ts'
  writeFileEnsured(join(outDir, clientRel), emitClient(operations))
  generatedFiles.push(clientRel)

  const notes = excluded.map(
    ({ spec }) => `  - ${spec.operationId}: ${EXCLUDED_OPERATIONS[spec.operationId].reason}`,
  )
  console.log(
    `[gen:bff-routes] ${generated.length} proxy stubs + manifest + client ` +
      `(${operations.length} operations) written under ${relative(process.cwd(), outDir) || outDir}\n` +
      `  hand-written (excluded):\n${notes.join('\n')}`,
  )
  return { manifest, generatedFiles }
}

// ---------------------------------------------------------------------------
// Check mode — drift + wiring validation
// ---------------------------------------------------------------------------

/** Discover committed route files under server/api/v1 (mirrors routeCoverage.spec.ts). */
function discoverHandRoutes(apiDir: string): Array<{ key: string; relFile: string }> {
  const out: Array<{ key: string; relFile: string }> = []
  const walk = (dir: string) => {
    for (const entry of readdirSync(dir)) {
      if (entry === '__tests__' || entry === 'generated') continue
      const full = join(dir, entry)
      if (statSync(full).isDirectory()) {
        walk(full)
        continue
      }
      const m = /^(?<route>.+)\.(?<method>get|post|put|delete)\.ts$/.exec(entry)
      if (!m?.groups) continue
      const parentRel = relative(apiDir, dir)
        .split('/')
        .filter(Boolean)
        .map((s) => {
          const b = /^\[(?<name>.+)\]$/.exec(s)
          return b?.groups?.name ? `{${b.groups.name}}` : s
        })
      const fileSegments = m.groups.route.split('/').map((s) => {
        const b = /^\[(?<name>.+)\]$/.exec(s)
        return b?.groups?.name ? `{${b.groups.name}}` : s
      })
      if (fileSegments[fileSegments.length - 1] === 'index') fileSegments.pop()
      out.push({
        key: `${m.groups.method.toUpperCase()} /api/v1/${[...parentRel, ...fileSegments].join('/')}`,
        relFile: relative(apiDir, full),
      })
    }
  }
  walk(apiDir)
  return out
}

function check(pluginDir: string, outDir: string, generatedFiles: string[]): void {
  const failures: string[] = []

  // 1. Drift: the in-tree generated output must match a fresh generation.
  for (const rel of generatedFiles) {
    const committed = join(pluginDir, rel)
    const fresh = join(outDir, rel)
    if (!existsSync(committed)) {
      failures.push(`missing generated file ${rel} — run \`pnpm gen:bff-routes\``)
      continue
    }
    if (readFileSync(committed, 'utf8') !== readFileSync(fresh, 'utf8')) {
      failures.push(`generated file drifted from docs/openapi.json: ${rel}`)
    }
  }
  const generatedRoot = join(pluginDir, 'server/api/v1/generated')
  for (const rel of listFilesRecursive(generatedRoot)) {
    const relFromPlugin = `server/api/v1/generated/${rel}`
    if (!generatedFiles.includes(relFromPlugin)) {
      failures.push(
        `stale generated file ${relFromPlugin} is no longer produced — run \`pnpm gen:bff-routes\` to clean up`,
      )
    }
  }

  // 2. Every excluded operation must still have its hand-written route file.
  for (const [operationId, meta] of Object.entries(EXCLUDED_OPERATIONS)) {
    if (!existsSync(join(pluginDir, 'server/api/v1', meta.handRoute))) {
      failures.push(
        `excluded operation ${operationId} is missing its hand-written route ` +
          `server/api/v1/${meta.handRoute} (${meta.reason})`,
      )
    }
  }

  // 3. No committed route file may shadow a generated BFF path.
  const manifestPath = join(outDir, 'server/api/v1/generated/manifest.json')
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8')) as RouteManifest
  const generatedKeys = new Set(manifest.routes.map((r) => `${r.method} ${r.bffPath}`))
  for (const hand of discoverHandRoutes(join(pluginDir, 'server/api/v1'))) {
    if (generatedKeys.has(hand.key)) {
      failures.push(
        `hand-written route ${hand.relFile} shadows a generated BFF route (${hand.key}) — ` +
          'delete it or add the operation to EXCLUDED_OPERATIONS',
      )
    }
  }

  if (failures.length > 0) {
    console.error(`[check:bff-routes] FAILED:\n  - ${failures.join('\n  - ')}`)
    process.exit(1)
  }
  console.log(
    `[check:bff-routes] OK — ${generatedFiles.length} generated files match docs/openapi.json, ` +
      `${Object.keys(EXCLUDED_OPERATIONS).length} hand-written custom routes intact.`,
  )
}

// ---------------------------------------------------------------------------
// CLI
// ---------------------------------------------------------------------------

function argValue(argv: string[], name: string): string | undefined {
  const i = argv.indexOf(name)
  return i >= 0 ? argv[i + 1] : undefined
}

function main(): void {
  const argv = process.argv.slice(2)
  const isCheck = argv.includes('--check')
  const pluginDir = argValue(argv, '--plugin-dir')
  const outDir = argValue(argv, '--outdir')
  const specArg = argValue(argv, '--spec')
  if (!pluginDir) throw new Error('[gen:bff-routes] --plugin-dir is required')
  const specPath = specArg ?? join(pluginDir, '..', '..', 'docs', 'openapi.json')
  const target = outDir ?? pluginDir
  const { generatedFiles } = generate(specPath, pluginDir, target)
  if (isCheck) check(pluginDir, target, generatedFiles)
}

try {
  main()
} catch (err) {
  console.error((err as Error).message)
  process.exit(1)
}
