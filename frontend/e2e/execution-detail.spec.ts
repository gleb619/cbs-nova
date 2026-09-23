// Execution detail smoke spec.
// Verifies the /executions/[id] route: an unknown id must surface the branded
// error state (not a blank page), and clicking a row on /executions must land
// on a rendered detail page. Selectors are the data-testid attributes from
// T257-T261 plus `execution-detail` / `execution-summary` / `run-again-button`;
// this spec is backend-optional: it works against a live backend (rows +
// click-through) and against a dead backend (error banner) alike. The
// click-through half skips cleanly when the list has no rows.

import { expect, test } from './fixtures'

test('unknown execution id shows a branded error state, not a blank page', async ({ page }) => {
  await page.goto('/executions/nonexistent-e2e-xyz')

  // Backend up → 404 → ErrorBanner; backend down → same banner or the Nuxt
  // error page. Either way, some documented error affordance must appear.
  const errorBanner = page.getByTestId('error-banner')
  const errorPage = page.getByTestId('error-page')
  await expect(errorBanner.or(errorPage).first()).toBeVisible({ timeout: 15_000 })

  // The detail container stays mounted in every template state (error /
  // loading / detail), proving the route did not white-screen.
  await expect(page.getByTestId('execution-detail')).toBeAttached()
})

test('clicking a list row navigates to a rendered execution detail page', async ({ page }) => {
  await page.goto('/executions')

  // Rows only exist when the backend is reachable and has executions. The
  // dead-backend / empty-backend path shows the error banner or the "No
  // executions match current filters" rowless table instead.
  const rows = page.locator('tr[data-testid^="execution-list-row-"]')
  await rows
    .first()
    .waitFor({ state: 'visible', timeout: 15_000 })
    .catch(() => {})

  if ((await rows.count()) === 0) {
    // Backend is down or has zero executions — nothing to click through to.
    test.skip()
  }

  await rows.first().click()
  await page.waitForURL(/\/executions\/.+/)

  // The detail must actually render: the summary card, the run-again button,
  // or the tab bar (I/O Payload is always present). `.first()` tolerates more
  // than one being visible.
  const summary = page.getByTestId('execution-summary')
  const runAgain = page.getByTestId('run-again-button')
  const payloadTab = page.getByRole('button', { name: 'I/O Payload', exact: true })
  await expect(summary.or(runAgain).or(payloadTab).first()).toBeVisible({ timeout: 15_000 })
})
