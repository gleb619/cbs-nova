// T777 — axe accessibility scan across the highest-value admin UI routes.
// Backend-optional: webServer points BFF at dead 127.1:1 by default, so the
// BFF surfaces its branded error state instead of live data. Spec asserts
// on rendered chrome + branded error state, NOT data fidelity.
//
// Allowlist rules:
//   - `MONACO`: third-party DOM under `.monaco-editor` — excluded at source
//     via AxeBuilder.exclude('.monaco-editor'); axe cannot reason about
//     virtualised/shadow DOM and flags false positives.
//   - `NUXT_DEVTOOLS`: dev-only inspector frame (`nuxt-devtools-frame`) —
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

const ROUTES = ['/', '/executions', '/runner', '/dsl-workbench', '/schedules'] as const

const ALLOWLIST: ReadonlyArray<{
  ruleId: string
  pages: ReadonlyArray<(typeof ROUTES)[number]>
  reason: string
}> = [
  {
    ruleId: 'color-contrast',
    pages: ['/', '/executions', '/runner', '/dsl-workbench', '/schedules'],
    reason:
      'T453 owns color tokens / contrast. Hits are neutral-500 placeholder ' +
      'chrome + sidebar-link text. Re-run after T453 lands.',
  },
]

function describeViolations(violations: { id: string; nodes: { target: string[] }[] }[]) {
  return violations
    .map((v) => `    ${v.id} (${v.nodes.length})  ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)
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
      .exclude('.nuxt-devtools-frame')

    const { violations } = await builder.analyze()

    const blocking = violations.filter((v) => {
      if (v.impact !== 'serious' && v.impact !== 'critical') return false
      return !ALLOWLIST.some(
        (entry) => entry.ruleId === v.id && entry.pages.includes(route),
      )
    })

    if (blocking.length > 0) {
      throw new Error(
        `axe-core reported ${blocking.length} serious/critical violation(s) on ${route}:\n${describeViolations(blocking)}`,
      )
    }
  })
}