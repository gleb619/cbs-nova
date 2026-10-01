import { expect, test } from './fixtures'

test('approvals page renders shell plus filter and table or empty/error state', async ({ page }) => {
  await page.goto('/approvals')

  await expect(page.getByRole('heading', { name: 'Approvals', level: 1 })).toBeVisible()
  await expect(page.getByTestId('approvals-status-filter')).toBeVisible()

  const table = page.getByTestId('approvals-table')
  const empty = page.getByTestId('approvals-empty')
  const loading = page.getByTestId('approvals-loading')
  const error = page.getByTestId('approvals-error')
  const banner = page.getByTestId('error-banner')
  const errorPage = page.getByTestId('error-page')

  await expect(
    table.or(empty).or(loading).or(error).or(banner).or(errorPage).first(),
  ).toBeVisible({ timeout: 15_000 })
})