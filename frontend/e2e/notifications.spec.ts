import { expect, test } from './fixtures'

test('notifications page renders shell plus rule list or empty/error state', async ({ page }) => {
  await page.goto('/notifications')

  await expect(page.getByRole('heading', { name: 'Notifications', level: 1 })).toBeVisible()
  await expect(page.getByTestId('notifications-page')).toBeVisible()
  await expect(page.getByTestId('notification-rule-list')).toBeVisible()

  const loading = page.getByTestId('notification-rule-list-loading')
  const empty = page.getByTestId('notification-rule-list-empty')
  const error = page.getByTestId('notification-rule-list-error')
  const banner = page.getByTestId('error-banner')
  const errorPage = page.getByTestId('error-page')

  await expect(
    loading.or(empty).or(error).or(banner).or(errorPage).first(),
  ).toBeVisible({ timeout: 15_000 })
})