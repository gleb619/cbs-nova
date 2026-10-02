import { describe, expect, it } from 'vitest'
import { parseSecurityHeadersEnv } from '../parseSecurityHeadersEnv'

describe('parseSecurityHeadersEnv', () => {
  it('returns undefined when the env var is unset', () => {
    expect(parseSecurityHeadersEnv(undefined)).toBeUndefined()
  })

  it('returns undefined for empty / whitespace input', () => {
    expect(parseSecurityHeadersEnv('')).toBeUndefined()
    expect(parseSecurityHeadersEnv('   ')).toBeUndefined()
  })

  it('returns false for the explicit kill-switch values', () => {
    expect(parseSecurityHeadersEnv('false')).toBe(false)
    expect(parseSecurityHeadersEnv('  false  ')).toBe(false)
    expect(parseSecurityHeadersEnv('0')).toBe(false)
  })

  it('parses a JSON object as the per-header override map', () => {
    expect(parseSecurityHeadersEnv('{"X-Frame-Options":"DENY"}')).toEqual({
      'X-Frame-Options': 'DENY',
    })
  })

  it('preserves null overrides (header removal)', () => {
    expect(parseSecurityHeadersEnv('{"X-Frame-Options":null}')).toEqual({
      'X-Frame-Options': null,
    })
  })

  it('coerces non-string values to strings', () => {
    expect(parseSecurityHeadersEnv('{"X-Foo":true,"X-Bar":42}')).toEqual({
      'X-Foo': 'true',
      'X-Bar': '42',
    })
  })

  it('returns undefined for malformed JSON instead of throwing', () => {
    expect(parseSecurityHeadersEnv('{not json')).toBeUndefined()
  })

  it('returns undefined for JSON arrays / scalars (only objects are valid)', () => {
    expect(parseSecurityHeadersEnv('[]')).toBeUndefined()
    expect(parseSecurityHeadersEnv('"X"')).toBeUndefined()
    expect(parseSecurityHeadersEnv('123')).toBeUndefined()
  })
})
