import { execSync } from 'node:child_process'
import { describe, expect, it } from 'vitest'

describe('v-html sanitization guard', () => {
  it('every v-html usage either is static or sanitizes through sanitizeHtml', () => {
    const output = execSync('grep -rl "v-html" src/components', {
      cwd: process.cwd(),
      encoding: 'utf-8',
    })
    const files = output.split('\n').filter(Boolean)
    expect(files.length).toBeGreaterThan(0)

    for (const file of files) {
      if (file.endsWith('ConstructStatusIcon.vue')) {
        continue
      }
      const content = execSync(`cat "${file}"`, { cwd: process.cwd(), encoding: 'utf-8' })
      const hasSanitizer =
        content.includes('sanitizeHtml(') ||
        content.includes('sanitizeHtml,') ||
        content.includes("from '../../utils/sanitizeHtml'")
      expect(hasSanitizer, `${file} renders v-html without sanitizeHtml`).toBe(true)
    }
  })
})
