// Schedules route smoke spec.
// Verifies the /schedules page renders the heading and either the list
// (rows) or the documented empty/error state. Selectors are the data-testid
// attributes from ScheduleList.vue; this spec is backend-optional: it works
// against a live backend (rows) and against a dead backend (empty/error /
// error banner) alike.

import { expect, test } from './fixtures'

test('schedules page renders shell plus list or empty/error state', async ({ page }) => {
  await page.goto('/schedules')

  await expect(page.getByRole('heading', { name: 'Schedules', level: 1 })).toBeVisible()
  await expect(page.getByTestId('schedule-list')).toBeVisible()

  // At least one of these must render — rows when the backend returns data,
  // the documented empty state when there are zero schedules, the inline list
  // error when the backend returns an error payload, or the branded error
  // banner / error page when the backend is unreachable.
  // `.first()` avoids Playwright's strict-mode violation when more than one
  // of them is present.
  const row = page.getByTestId('schedule-row')
  const empty = page.getByTestId('schedule-list-empty')
  const listError = page.getByTestId('schedule-list-error')
  const banner = page.getByTestId('error-banner')
  const errorPage = page.getByTestId('error-page')

  await expect(row.or(empty).or(listError).or(banner).or(errorPage).first()).toBeVisible({
    timeout: 15_000,
  })
})
