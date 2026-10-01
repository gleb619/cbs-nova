import { expect, test } from './fixtures'

test('webhooks page renders shell plus deliveries panel or empty/error state', async ({ page }) => {
  await page.goto('/webhooks')

  await expect(page.getByRole('heading', { name: 'Webhooks', level: 1 })).toBeVisible()
  await expect(page.getByTestId('webhooks-page')).toBeVisible()
  await expect(page.getByTestId('webhook-deliveries-panel')).toBeVisible()

  const table = page.getByTestId('webhook-deliveries-table')
  const empty = page.getByTestId('webhook-deliveries-empty')
  const loading = page.getByTestId('webhook-deliveries-loading')
  const error = page.getByTestId('webhook-deliveries-error')
  const banner = page.getByTestId('error-banner')
  const errorPage = page.getByTestId('error-page')

  await expect(
    table.or(empty).or(loading).or(error).or(banner).or(errorPage).first(),
  ).toBeVisible({ timeout: 15_000 })
})