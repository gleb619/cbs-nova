// T777 — axe accessibility scan across the highest-value admin UI routes.
// Backend-optional: webServer points BFF at dead 127.1:1 by default, so the
// BFF surfaces its branded error state instead of live data. Spec asserts
// on rendered chrome + branded error state, NOT data fidelity.
//
// Allowlist rules:
//   - `MONACO`: third-party DOM under `.monaco-editor` — excluded at source
//     via AxeBuilder.exclude('.monaco-editor'); axe cannot reason about
//     virtualised/shadow DOM and flags false positives.
//   - `NUXT_DEVTOOLS`: dev-only inspector (`nuxt-devtools-frame`, `#nuxt-devtools-*`) —
//     same reasoning: third-party DOM, not shipped to production.
//   - `color-contrast`: tokens/contrast are owned by T453 color migration.
//     Many hits come from neutral-500 placeholder text on neutral-50
//     backgrounds and from the dark-mode contrast rules. Each entry below
//     records which page + selector cluster, so the next pass knows what
//     to re-run after T453 lands.
//   - All entries MUST list rule id + selector/page + reason. No blanket
//     disables. Add or remove entries as violations get fixed.

import { AxeBuilder } from '@axe-core/playwright'
import { expect, test } from './fixtures'

const ROUTES = [
  '/',
  '/executions',
  '/runner',
  '/dsl-workbench',
  '/schedules',
  // T741 pages
  '/activity',
  '/approvals',
  '/notifications',
  '/promote',
  '/webhooks',
] as const

// Ratchet allowlist: a rule may appear on a route up to `maxNodes` times. Anything above that (a
// new offending element) fails, so the baseline can only shrink. Counts measured with
// `A11Y_REPORT=1` on 2026-10-02 against the dead-backend dev server.
const ALLOWLIST: ReadonlyArray<{
  ruleId: string
  maxNodes: Readonly<Record<(typeof ROUTES)[number], number>>
  reason: string
}> = [
  {
    ruleId: 'color-contrast',
    maxNodes: {
      '/': 9,
      '/executions': 7,
      '/runner': 11,
      '/dsl-workbench': 12,
      '/schedules': 8,
      '/activity': 8,
      '/approvals': 9,
      '/notifications': 8,
      '/promote': 11,
      '/webhooks': 8,
    },
    reason:
      'T453 owns color tokens / contrast (raw Tailwind neutral-500 placeholder + sidebar-link ' +
      'text). Lower these numbers, never raise them; delete the entry once T453 lands.',
  },
]

function describeViolations(violations: { id: string; nodes: { target: string[] }[] }[]) {
  return violations
    .map(
      (v) =>
        `    ${v.id} (${v.nodes.length})  ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`,
    )
    .join('\n')
}

for (const route of ROUTES) {
  test(`${route} has no serious/critical WCAG 2 A/AA violations`, async ({ page }) => {
    await page.goto(route)
    await expect(page.locator('body')).not.toBeEmpty()
    await page.waitForLoadState('domcontentloaded')

    const builder = new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      // Third-party / dev-only DOM axe cannot reason about.
      .exclude('.monaco-editor')
      .exclude('nuxt-devtools-frame')
      .exclude('#nuxt-devtools-anchor')
      .exclude('#nuxt-devtools-container')
      .exclude('.nuxt-devtools-label-main')

    const { violations } = await builder.analyze()

    // `A11Y_REPORT=1 pnpm exec playwright test e2e/a11y.spec.ts` prints every serious/critical
    // finding (allowlisted or not) as `route rule impact nodes` — used to size the ratchet below.
    if (process.env.A11Y_REPORT) {
      for (const v of violations) {
        if (v.impact === 'serious' || v.impact === 'critical') {
          console.log(`A11Y ${route} ${v.id} ${v.impact} nodes=${v.nodes.length}`)
        }
      }
    }

    const blocking = violations.filter((v) => {
      if (v.impact !== 'serious' && v.impact !== 'critical') return false
      const entry = ALLOWLIST.find((e) => e.ruleId === v.id)
      return !entry || v.nodes.length > entry.maxNodes[route]
    })

    if (blocking.length > 0) {
      throw new Error(
        `axe-core reported ${blocking.length} serious/critical violation(s) on ${route}:\n${describeViolations(blocking)}`,
      )
    }
  })
}
