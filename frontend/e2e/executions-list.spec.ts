// Executions list smoke spec.
// Verifies the /executions page renders the filter UI and either the list
// (rows) or the documented empty/error state. Selectors are the data-testid
// attributes added in T257-T261; this spec is backend-optional: it works
// against a live backend (rows) and against a dead backend (error banner /
// empty state) alike.

import { expect, test } from './fixtures'

test('executions list renders filter UI plus list or empty/error state', async ({ page }) => {
  await page.goto('/executions')

  await expect(page.getByRole('heading', { name: 'Executions', level: 1 })).toBeVisible()

  const filters = page.getByTestId('execution-filters')
  await expect(filters).toBeVisible()

  // At least one of these three must render — rows when the backend returns
  // data, the documented empty state when there are zero executions, or the
  // branded error banner when the backend is unreachable. `.first()` avoids
  // Playwright's strict-mode violation when more than one of them is present.
  const list = page.getByTestId('execution-list')
  const errorBanner = page.getByTestId('error-banner')
  const errorPage = page.getByTestId('error-page')

  await expect(list.or(errorBanner).or(errorPage).first()).toBeVisible({ timeout: 15_000 })
})
