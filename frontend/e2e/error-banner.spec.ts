// Error-state smoke spec.
// Confirms that when the Nitro BFF is pointed at a dead backend port the UI
// shows the branded error banner (T257 / ErrorBanner.vue) — NOT a blank page
// or an unstyled stack trace. This is the regression net for "the BFF talks
// to something, but that something is down."

import { expect, test } from './fixtures'

test('dead backend surfaces branded error banner, not a blank page', async ({ page }) => {
  // The webServer is already configured to point at 127.0.0.1:1 by default;
  // navigating to a data-driven page should therefore fail over to the
  // branded error state.
  await page.goto('/executions')

  // The page chrome must still render — header / heading — proving we did
  // NOT land on a blank screen.
  await expect(page.getByRole('heading', { name: 'Executions', level: 1 })).toBeVisible()

  const errorBanner = page.getByTestId('error-banner')
  const errorPage = page.getByTestId('error-page')

  // At least one of the documented error affordances must appear.
  await expect(errorBanner.or(errorPage).first()).toBeVisible({ timeout: 15_000 })

  // And the Nuxt error page must NOT show its raw statusCode alone without
  // our branding — sanity check that the layout is still mounted.
  await expect(page.locator('body')).not.toBeEmpty()
})
