import { describe, expect, it } from 'vitest'
import { DSL_TEMPLATES } from '../dslTemplates'

describe('dslTemplates', () => {
  it('exports four starter templates with required metadata', () => {
    expect(DSL_TEMPLATES).toHaveLength(4)

    const ids = DSL_TEMPLATES.map((t) => t.id)
    expect(ids).toContain('plain-process')
    expect(ids).toContain('saga')
    expect(ids).toContain('http-pipeline')
    expect(ids).toContain('retry-policy')
  })

  it('every template body is valid JSON and has the required draft keys', () => {
    for (const template of DSL_TEMPLATES) {
      let parsed: unknown
      expect(() => {
        parsed = JSON.parse(template.body)
      }).not.toThrow()

      expect(parsed).toMatchObject({
        name: expect.any(String),
        type: expect.any(String),
        status: expect.any(String),
        version: expect.any(String),
      })
    }
  })

  it('templates carry distinct labels and descriptions', () => {
    const labels = new Set(DSL_TEMPLATES.map((t) => t.label))
    expect(labels.size).toBe(DSL_TEMPLATES.length)

    for (const template of DSL_TEMPLATES) {
      expect(template.description).toBeTruthy()
    }
  })

  it('http-pipeline template references the httpCall helper', () => {
    const http = DSL_TEMPLATES.find((t) => t.id === 'http-pipeline')
    expect(http).toBeDefined()
    const parsed = JSON.parse(http?.body ?? '{}') as { steps?: { helper?: string }[] }
    const helpers = parsed.steps?.map((s) => s.helper) ?? []
    expect(helpers).toContain('httpCall')
  })

  it('saga template includes compensation configuration', () => {
    const saga = DSL_TEMPLATES.find((t) => t.id === 'saga')
    expect(saga).toBeDefined()
    const parsed = JSON.parse(saga?.body ?? '{}') as { compensation?: unknown }
    expect(parsed.compensation).toBeDefined()
  })

  it('retry-policy template includes retry configuration', () => {
    const retry = DSL_TEMPLATES.find((t) => t.id === 'retry-policy')
    expect(retry).toBeDefined()
    const parsed = JSON.parse(retry?.body ?? '{}') as { retry?: unknown }
    expect(parsed.retry).toBeDefined()
  })
})
