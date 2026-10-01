import { expect, test } from './fixtures'

test('activity page renders shell plus feed or empty/error state', async ({ page }) => {
  await page.goto('/activity')

  await expect(page.getByRole('heading', { name: 'Activity', level: 1 })).toBeVisible()
  await expect(page.getByTestId('activity-page')).toBeVisible()
  await expect(page.getByTestId('activity-feed')).toBeVisible()

  const list = page.getByTestId('activity-list')
  const empty = page.getByTestId('activity-empty')
  const error = page.getByTestId('activity-error')
  const banner = page.getByTestId('error-banner')
  const errorPage = page.getByTestId('error-page')

  await expect(list.or(empty).or(error).or(banner).or(errorPage).first()).toBeVisible({
    timeout: 15_000,
  })
})