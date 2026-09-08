import { describe, expect, it } from 'vitest'
import { buildHelperSnippet } from '../helperSnippet'

describe('buildHelperSnippet', () => {
  it('renders the full call with input and output types', () => {
    expect(
      buildHelperSnippet({ name: 'uuidV7', inputType: 'UuidV7In', outputType: 'UuidV7Out' }),
    ).toBe('ctx.runHelper("uuidV7", new UuidV7In()).as(UuidV7Out.class)')
  })

  it('passes null and drops the cast when both types are missing', () => {
    expect(buildHelperSnippet({ name: 'x' })).toBe('ctx.runHelper("x", null)')
  })

  it('drops the cast when only the input type is known', () => {
    expect(buildHelperSnippet({ name: 'x', inputType: 'XIn' })).toBe(
      'ctx.runHelper("x", new XIn())',
    )
  })

  it('passes null when only the output type is known', () => {
    expect(buildHelperSnippet({ name: 'x', outputType: 'XOut' })).toBe(
      'ctx.runHelper("x", null).as(XOut.class)',
    )
  })

  it('treats blank and null types as missing', () => {
    expect(buildHelperSnippet({ name: 'x', inputType: '  ', outputType: null })).toBe(
      'ctx.runHelper("x", null)',
    )
  })

  it('trims surrounding whitespace from types', () => {
    expect(buildHelperSnippet({ name: 'x', inputType: ' XIn ', outputType: ' XOut ' })).toBe(
      'ctx.runHelper("x", new XIn()).as(XOut.class)',
    )
  })
})
