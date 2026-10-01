import { expect, test } from './fixtures'

test('promote page renders shell plus source select or empty/error state', async ({ page }) => {
  await page.goto('/promote')

  await expect(page.getByRole('heading', { name: 'Promote', level: 1 })).toBeVisible()
  await expect(page.getByTestId('promote-page')).toBeVisible()
  await expect(page.getByTestId('promote-source-select')).toBeVisible()

  const placeholder = page.getByTestId('promote-preview-placeholder')
  const noEnvs = page.getByTestId('promote-no-environments')
  const envsError = page.getByTestId('promote-environments-error')
  const banner = page.getByTestId('error-banner')
  const errorPage = page.getByTestId('error-page')

  await expect(
    placeholder.or(noEnvs).or(envsError).or(banner).or(errorPage).first(),
  ).toBeVisible({ timeout: 15_000 })
})