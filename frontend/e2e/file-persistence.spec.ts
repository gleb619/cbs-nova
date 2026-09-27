// File & draft persistence spec (backend-required).
//
// Saves an edit from the DSL workbench code and docs tabs, reloads the page,
// and asserts the change survived (server round-trip). Original content is
// restored afterwards so the spec leaves the workspace untouched.

import { existsSync, unlinkSync } from 'node:fs'
import { resolve } from 'node:path'
import type { APIRequestContext } from '@playwright/test'
import { expect, backendRequired as test } from './fixtures'

const CONSTRUCT = 'ApiKeyProvisioning'
const DOC_PATH = resolve(
  process.cwd(),
  '../backend/dsl-starter/dsl-examples/docs/api-key-provisioning.md',
)

async function readByName(request: APIRequestContext, kind: 'files' | 'docs') {
  const response = await request.get(`/api/v1/dsl/${kind}/by-name/${CONSTRUCT}`)
  if (!response.ok()) return null
  return ((await response.json()) as { content: string }).content
}

test.describe('dsl workbench file persistence', () => {
  test('code tab save survives reload', async ({ page }) => {
    const original = await readByName(page.request, 'files')
    test.skip(original === null, `${CONSTRUCT} source file must exist on the backend`)
    const marker = `// e2e-probe-${Date.now()}`

    try {
      await page.goto(`/dsl-workbench?activeTab=code`)
      await page.getByTestId(`plain-construct-list-item-${CONSTRUCT}`).click()
      const editor = page.getByTestId('code-tab-editor')
      await editor.click()
      await page.keyboard.press('Control+End')
      await page.keyboard.press('Enter')
      await page.keyboard.type(marker)
      await page.getByTestId('code-tab-save').click()

      // Builder auto-flushes staged writes on an interval; wait until the queue drains.
      await expect
        .poll(async () => {
          const status = await page.request.get('/api/v1/dsl/files/status')
          return ((await status.json()) as { pending: number }).pending
        })
        .toBe(0)

      // Server-side: the flushed file carries the marker.
      await expect.poll(() => readByName(page.request, 'files')).toContain(marker)

      // UI-side: a hard reload shows the persisted content in the editor.
      await page.reload()
      await page.getByTestId(`plain-construct-list-item-${CONSTRUCT}`).click()
      await editor.click()
      await page.keyboard.press('Control+End')
      await expect(page.locator('[data-testid="code-tab-editor"] .view-lines')).toContainText(
        marker,
      )
    } finally {
      await page.request.post(`/api/v1/dsl/files/by-name/${CONSTRUCT}`, {
        data: { content: original },
      })
    }
  })

  test('docs tab raw save survives reload', async ({ page }) => {
    const original = await readByName(page.request, 'docs')
    const marker = `e2e-probe-doc-${Date.now()}`

    try {
      await page.goto(`/dsl-workbench?activeTab=docs`)
      await page.getByTestId(`plain-construct-list-item-${CONSTRUCT}`).click()
      await page.getByTestId('doc-tab-mode-raw').click()
      const editor = page.getByTestId('doc-tab-raw')
      await editor.click()
      await page.keyboard.press('Control+End')
      await page.keyboard.press('Enter')
      await page.keyboard.type(marker)
      await page.getByTestId('doc-tab-save').click()

      await expect
        .poll(async () => {
          const status = await page.request.get('/api/v1/dsl/docs/status')
          return ((await status.json()) as { pending: number }).pending
        })
        .toBe(0)

      await expect.poll(() => readByName(page.request, 'docs')).toContain(marker)

      await page.reload()
      await page.getByTestId(`plain-construct-list-item-${CONSTRUCT}`).click()
      await page.getByTestId('doc-tab-mode-raw').click()
      await expect(page.locator('[data-testid="doc-tab-raw"] .view-lines')).toContainText(marker)
    } finally {
      if (original !== null) {
        await page.request.post(`/api/v1/dsl/docs/by-name/${CONSTRUCT}`, {
          data: { content: original },
        })
      } else if (existsSync(DOC_PATH)) {
        unlinkSync(DOC_PATH)
      }
    }
  })
})
