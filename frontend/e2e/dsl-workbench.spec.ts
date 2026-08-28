// DSL workbench smoke spec.
// Verifies the /dsl-workbench page mounts the construct explorer (T257) and
// its header. The actual construct list may be empty when the backend is
// unreachable; we only assert the explorer shell is present.

import { expect, test } from './fixtures'

test('dsl workbench renders construct explorer shell', async ({ page }) => {
  await page.goto('/dsl-workbench')

  const explorer = page.getByTestId('construct-explorer')
  await expect(explorer).toBeVisible()

  await expect(page.getByTestId('construct-explorer-header')).toBeVisible()

  // The explorer hosts the construct list (or its empty/loading skeleton).
  // `construct-list-skeleton` covers the in-flight fetch; `plain-construct-list`
  // covers the resolved tree.
  const listOrSkeleton = page
    .getByTestId('plain-construct-list')
    .or(page.getByTestId('construct-list-skeleton'))
  await expect(listOrSkeleton).toBeVisible({ timeout: 15_000 })
})
