import { describe, expect, it } from 'vitest'
import { parseForwardedHeadersEnv } from '../parseForwardedHeadersEnv'

describe('parseForwardedHeadersEnv', () => {
  it('returns undefined when the env var is unset', () => {
    expect(parseForwardedHeadersEnv(undefined)).toBeUndefined()
  })

  it('returns undefined for an empty string', () => {
    expect(parseForwardedHeadersEnv('')).toBeUndefined()
  })

  it('returns the same map for a valid object', () => {
    const raw = '{"x-tenant-id":"X-Tenant-Id","x-correlation-id":"X-Correlation-Id"}'

    expect(parseForwardedHeadersEnv(raw)).toEqual({
      'x-tenant-id': 'X-Tenant-Id',
      'x-correlation-id': 'X-Correlation-Id',
    })
  })

  it('returns an empty map for an empty JSON object', () => {
    expect(parseForwardedHeadersEnv('{}')).toEqual({})
  })

  it('throws SyntaxError on invalid JSON', () => {
    expect(() => parseForwardedHeadersEnv('{not json')).toThrow(SyntaxError)
  })

  it.each([
    ['null', 'null'],
    ['an array', '["x-tenant-id"]'],
    ['a number', '42'],
    ['a string', '"x-tenant-id"'],
  ])('throws "must be a JSON object" for %s', (_label, raw) => {
    expect(() => parseForwardedHeadersEnv(raw)).toThrow(/must be a JSON object/)
  })

  it.each([
    ['a number value', '{"x-tenant-id":1}', 'x-tenant-id', 'number'],
    ['a boolean value', '{"x-tenant-id":true}', 'x-tenant-id', 'boolean'],
    ['a null value', '{"x-tenant-id":null}', 'x-tenant-id', 'object'],
    ['a nested object', '{"x-tenant-id":{"a":"b"}}', 'x-tenant-id', 'object'],
  ])('throws naming the key and typeof for %s', (_label, raw, key, type) => {
    expect(() => parseForwardedHeadersEnv(raw)).toThrow(
      `BACKEND_FORWARDED_HEADERS.${key} must map to a string ` +
        `(outbound header name), got ${type}.`,
    )
  })
})
