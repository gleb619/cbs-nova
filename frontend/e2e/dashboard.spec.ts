// Dashboard smoke spec.
// Verifies the / page renders the dashboard root plus the H1 and either the
// loaded/loading/error shell. Selectors are the data-testid attributes added
// for the dashboard tiles; this spec is backend-optional: it works against a
// live backend (stats tile) and against a dead backend (error banner / error
// page) alike.

import { expect, test } from './fixtures'

test('dashboard renders root plus loaded, loading, or error shell', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible()

  const dashboard = page.getByTestId('dashboard')
  await expect(dashboard).toBeVisible()

  // At least one of these four must render — loaded stats tile, loading
  // skeleton, branded error banner, or full error page. `.first()` avoids
  // Playwright's strict-mode violation when more than one of them is present.
  const stats = page.getByTestId('dashboard-stats')
  const statsSkeleton = page.getByTestId('dashboard-stats-skeleton')
  const errorBanner = page.getByTestId('error-banner')
  const errorPage = page.getByTestId('error-page')

  await expect(stats.or(statsSkeleton).or(errorBanner).or(errorPage).first()).toBeVisible({
    timeout: 15_000,
  })

  // The recent runs table renders when data is present; the error banner
  // covers the unreachable-backend case. `.first()` is the same
  // strict-mode guard as above.
  const recentRunsTable = page.getByTestId('recent-runs-table')

  await expect(recentRunsTable.or(errorBanner).first()).toBeVisible({ timeout: 15_000 })
})
