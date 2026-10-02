import { describe, expect, it } from 'vitest'
import {
  buildSecurityHeaders,
  DEFAULT_SECURITY_HEADERS,
  HSTS_HEADER_VALUE,
} from '../securityHeaders'

describe('buildSecurityHeaders', () => {
  describe('defaults', () => {
    it('returns the documented default set when called with no options', () => {
      const headers = buildSecurityHeaders()
      expect(headers).toEqual({
        'X-Content-Type-Options': 'nosniff',
        'Referrer-Policy': 'strict-origin-when-cross-origin',
        'X-Frame-Options': 'SAMEORIGIN',
        'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
      })
    })

    it('does not emit HSTS by default (plain http in dev)', () => {
      const headers = buildSecurityHeaders()
      expect(headers['Strict-Transport-Security']).toBeUndefined()
    })

    it('DEFAULT_SECURITY_HEADERS is frozen and matches the builder output', () => {
      expect(Object.isFrozen(DEFAULT_SECURITY_HEADERS)).toBe(true)
      expect(buildSecurityHeaders()).toEqual({ ...DEFAULT_SECURITY_HEADERS })
    })
  })

  describe('HSTS', () => {
    it('adds HSTS only when isHttps is true', () => {
      expect(buildSecurityHeaders({ isHttps: true })['Strict-Transport-Security']).toBe(
        HSTS_HEADER_VALUE,
      )
    })

    it('omits HSTS when isHttps is false (explicit)', () => {
      expect(buildSecurityHeaders({ isHttps: false })['Strict-Transport-Security']).toBeUndefined()
    })
  })

  describe('opt-out (overrides: false)', () => {
    it('returns an empty header set when overrides is false', () => {
      expect(buildSecurityHeaders({ overrides: false })).toEqual({})
    })

    it('still respects HSTS when the layer is enabled but transport is https', () => {
      // `overrides: false` is the kill-switch for the WHOLE layer; HSTS is
      // also off so a deployment that opts out has a clean response.
      expect(buildSecurityHeaders({ overrides: false, isHttps: true })).toEqual({})
    })
  })

  describe('per-header overrides', () => {
    it('replaces a default header value', () => {
      const headers = buildSecurityHeaders({
        overrides: { 'X-Frame-Options': 'DENY' },
      })
      expect(headers['X-Frame-Options']).toBe('DENY')
      // Other defaults preserved.
      expect(headers['X-Content-Type-Options']).toBe('nosniff')
      expect(headers['Referrer-Policy']).toBe('strict-origin-when-cross-origin')
    })

    it('removes a default header when overridden with null', () => {
      const headers = buildSecurityHeaders({
        overrides: { 'X-Frame-Options': null },
      })
      expect(headers['X-Frame-Options']).toBeUndefined()
      expect(headers['X-Content-Type-Options']).toBe('nosniff')
    })

    it('adds a brand-new header the defaults do not include', () => {
      const headers = buildSecurityHeaders({
        overrides: { 'X-Custom-Flag': 'enabled' },
      })
      expect(headers['X-Custom-Flag']).toBe('enabled')
      expect(headers['X-Content-Type-Options']).toBe('nosniff')
    })

    it('handles mixed override/add/remove in one call', () => {
      const headers = buildSecurityHeaders({
        overrides: {
          'Referrer-Policy': 'no-referrer',
          'X-Frame-Options': null,
          'X-Custom': 'yes',
        },
      })
      expect(headers['Referrer-Policy']).toBe('no-referrer')
      expect(headers['X-Frame-Options']).toBeUndefined()
      expect(headers['X-Custom']).toBe('yes')
      expect(headers['X-Content-Type-Options']).toBe('nosniff')
    })
  })

  describe('output shape', () => {
    it('returns a fresh object every call (no shared mutation)', () => {
      const a = buildSecurityHeaders()
      const b = buildSecurityHeaders()
      expect(a).not.toBe(b)
      a['X-Content-Type-Options'] = 'tampered'
      expect(b['X-Content-Type-Options']).toBe('nosniff')
    })

    it('every key is a string and every value is a string', () => {
      const headers = buildSecurityHeaders({ isHttps: true })
      for (const [k, v] of Object.entries(headers)) {
        expect(typeof k).toBe('string')
        expect(typeof v).toBe('string')
      }
    })
  })
})
