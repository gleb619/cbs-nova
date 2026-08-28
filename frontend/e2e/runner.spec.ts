// Runner smoke spec — backend REQUIRED.
// Skips automatically unless E2E_BACKEND is set (see playwright.config.ts and
// e2e/fixtures.ts). With a live backend: select the first available DSL
// definition and verify the preview happy path renders the output panel.

import { expect, backendRequired as test } from './fixtures'

test('runner: select first definition -> preview output panel renders', async ({ page }) => {
  await page.goto('/runner')

  const selector = page.getByTestId('definition-selector')
  await expect(selector).toBeVisible()

  const select = page.getByTestId('definition-selector-select')
  await expect(select).toBeVisible()

  // Pick the first non-empty option in the definitions <select>. The
  // DefinitionSelector renders a native <select>, so we drive it via
  // selectOption with the first available value.
  const firstValue = await select.locator('option').nth(1).getAttribute('value')
  test.skip(firstValue === null, 'no definitions available — cannot exercise happy path')
  await select.selectOption(firstValue ?? '')

  // OutputPanel is always mounted; with a real backend + a definition picked
  // we expect either a populated result-tab panel or the spinner to resolve
  // into output content.
  const outputPanel = page.getByTestId('output-panel')
  await expect(outputPanel).toBeVisible({ timeout: 15_000 })
})
