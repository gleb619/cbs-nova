import { beforeEach, describe, expect, it, type Mock, vi } from 'vitest'

// Middleware spec (T773).
//
// Strategy: stub h3's `setResponseHeader` so we can assert every call the
// middleware makes without standing up a Nitro server. The middleware reads
// `useRuntimeConfig` and `event.node.res.getHeader` / `event.node.req.socket`;
// we wire those onto a small `fakeEvent` and reset between tests.

type HeaderMap = Record<string, string | undefined>
type ResMap = { headers: HeaderMap; getHeader: (name: string) => string | undefined }

let res: ResMap
let req: { headers: Record<string, string | string[]>; socket: { encrypted?: boolean } }

const setResponseHeaderMock: Mock = vi.fn()
const getResponseHeaderMock: Mock = vi.fn()

// Re-install the implementations on the mocks every test — `mockReset` would
// wipe them, and a wiped spy returns `undefined`, which would make the
// middleware overwrite pre-set host headers.
const rewireSpies = () => {
  setResponseHeaderMock.mockImplementation(
    (_event: unknown, name: string, value: string) => {
      res.headers[name.toLowerCase()] = value
    },
  )
  getResponseHeaderMock.mockImplementation((name: string) => res.headers[name.toLowerCase()])
}

vi.mock('h3', async (importOriginal) => {
  const actual = await importOriginal<typeof import('h3')>()
  return {
    ...actual,
    setResponseHeader: setResponseHeaderMock,
  }
})

// `useRuntimeConfig` is a Nitro global; the middleware calls it without an
// import (matches `backendHeaders.ts` etc.). The setup file installs a
// default mock on globalThis; specs override it per-test via
// `vi.mocked(useRuntimeConfig).mockReturnValue(...)`.
const setSecurityHeaders = (value: Record<string, string | null> | false | undefined) => {
  vi.mocked(useRuntimeConfig as Mock).mockReturnValue({ securityHeaders: value })
}

// Import after mocks.
const { default: securityHeadersMiddleware } = await import('../securityHeaders')

beforeEach(() => {
  setResponseHeaderMock.mockClear()
  getResponseHeaderMock.mockClear()
  setSecurityHeaders(undefined)
  rewireSpies()
  res = { headers: {}, getHeader: getResponseHeaderMock }
  req = { headers: {}, socket: {} }
})

describe('securityHeaders middleware', () => {
  it('attaches the default header set on a /api/v1/health-style request', () => {
    const event = { node: { res, req } } as unknown as Parameters<
      typeof securityHeadersMiddleware
    >[0]
    securityHeadersMiddleware(event)

    expect(setResponseHeaderMock).toHaveBeenCalledWith(event, 'X-Content-Type-Options', 'nosniff')
    expect(setResponseHeaderMock).toHaveBeenCalledWith(
      event,
      'Referrer-Policy',
      'strict-origin-when-cross-origin',
    )
    expect(setResponseHeaderMock).toHaveBeenCalledWith(event, 'X-Frame-Options', 'SAMEORIGIN')
    expect(setResponseHeaderMock).toHaveBeenCalledWith(
      event,
      'Permissions-Policy',
      'camera=(), microphone=(), geolocation=()',
    )
    // No HSTS on plain http.
    expect(setResponseHeaderMock).not.toHaveBeenCalledWith(
      event,
      'Strict-Transport-Security',
      expect.anything(),
    )
  })

  it('attaches the same default set on a page route (no path filtering)', () => {
    // The middleware intentionally runs on every response — page routes and
    // /api/v1/** alike. The util is path-agnostic, the middleware is too.
    const event = { node: { res, req } } as unknown as Parameters<
      typeof securityHeadersMiddleware
    >[0]
    securityHeadersMiddleware(event)

    const headerNames = setResponseHeaderMock.mock.calls.map((c) => c[1])
    expect(headerNames).toEqual(
      expect.arrayContaining([
        'X-Content-Type-Options',
        'Referrer-Policy',
        'X-Frame-Options',
        'Permissions-Policy',
      ]),
    )
  })

  it('only writes to res.headers (does not buffer a body, does not set status)', () => {
    // The middleware contract: SSE streams pass through unchanged. Assert
    // the only mutation the middleware makes to `res` is via
    // setResponseHeader (the only h3 import the source uses).
    const event = { node: { res, req } } as unknown as Parameters<
      typeof securityHeadersMiddleware
    >[0]
    securityHeadersMiddleware(event)

    expect(Object.keys(res.headers).sort()).toEqual(
      [
        'permissions-policy',
        'referrer-policy',
        'x-content-type-options',
        'x-frame-options',
      ].sort(),
    )
  })

  it('does not clobber a pre-set host header (e.g. Cache-Control from another handler)', () => {
    // Pre-populate res.headers BEFORE the middleware runs; the getHeader
    // stub returns it on lookup.
    res.headers['cache-control'] = 'no-store'
    const event = { node: { res, req } } as unknown as Parameters<
      typeof securityHeadersMiddleware
    >[0]
    securityHeadersMiddleware(event)

    // Cache-Control is not in our default set, so we never touch it.
    expect(setResponseHeaderMock).not.toHaveBeenCalledWith(
      event,
      'Cache-Control',
      expect.anything(),
    )
    expect(res.headers['cache-control']).toBe('no-store')
  })

  it('skips headers the response already has (does not overwrite)', () => {
    // The host app's reverse proxy or an earlier middleware may have set
    // X-Frame-Options for iframe-embedding. The middleware must respect that.
    res.headers['x-frame-options'] = 'ALLOW-FROM https://partner.example'
    const event = { node: { res, req } } as unknown as Parameters<
      typeof securityHeadersMiddleware
    >[0]
    securityHeadersMiddleware(event)

    expect(setResponseHeaderMock).not.toHaveBeenCalledWith(
      event,
      'X-Frame-Options',
      expect.anything(),
    )
    expect(res.headers['x-frame-options']).toBe('ALLOW-FROM https://partner.example')
  })

  describe('HSTS detection', () => {
    it('adds HSTS when the request is over TLS (socket.encrypted)', () => {
      req.socket.encrypted = true
      const event = { node: { res, req } } as unknown as Parameters<
        typeof securityHeadersMiddleware
      >[0]
      securityHeadersMiddleware(event)

      expect(setResponseHeaderMock).toHaveBeenCalledWith(
        event,
        'Strict-Transport-Security',
        'max-age=31536000; includeSubDomains',
      )
    })

    it('adds HSTS when X-Forwarded-Proto is https (reverse proxy)', () => {
      req.headers['x-forwarded-proto'] = 'https'
      const event = { node: { res, req } } as unknown as Parameters<
        typeof securityHeadersMiddleware
      >[0]
      securityHeadersMiddleware(event)

      expect(setResponseHeaderMock).toHaveBeenCalledWith(
        event,
        'Strict-Transport-Security',
        'max-age=31536000; includeSubDomains',
      )
    })

    it('adds HSTS when X-Forwarded-Proto is an array starting with https', () => {
      req.headers['x-forwarded-proto'] = ['https', 'http']
      const event = { node: { res, req } } as unknown as Parameters<
        typeof securityHeadersMiddleware
      >[0]
      securityHeadersMiddleware(event)

      expect(setResponseHeaderMock).toHaveBeenCalledWith(
        event,
        'Strict-Transport-Security',
        'max-age=31536000; includeSubDomains',
      )
    })

    it('does NOT add HSTS on plain http', () => {
      req.headers['x-forwarded-proto'] = 'http'
      const event = { node: { res, req } } as unknown as Parameters<
        typeof securityHeadersMiddleware
      >[0]
      securityHeadersMiddleware(event)

      expect(setResponseHeaderMock).not.toHaveBeenCalledWith(
        event,
        'Strict-Transport-Security',
        expect.anything(),
      )
    })
  })

  describe('opt-out', () => {
    it('attaches no headers when securityHeaders is explicitly false', () => {
      setSecurityHeaders(false)
      const event = { node: { res, req } } as unknown as Parameters<
        typeof securityHeadersMiddleware
      >[0]
      securityHeadersMiddleware(event)

      expect(setResponseHeaderMock).not.toHaveBeenCalled()
      expect(res.headers).toEqual({})
    })
  })

  describe('overrides', () => {
    it('replaces a default header with the override value', () => {
      setSecurityHeaders({ 'X-Frame-Options': 'DENY' })
      const event = { node: { res, req } } as unknown as Parameters<
        typeof securityHeadersMiddleware
      >[0]
      securityHeadersMiddleware(event)

      expect(setResponseHeaderMock).toHaveBeenCalledWith(event, 'X-Frame-Options', 'DENY')
    })

    it('omits a header when the override is null', () => {
      setSecurityHeaders({ 'X-Frame-Options': null })
      const event = { node: { res, req } } as unknown as Parameters<
        typeof securityHeadersMiddleware
      >[0]
      securityHeadersMiddleware(event)

      expect(setResponseHeaderMock).not.toHaveBeenCalledWith(
        event,
        'X-Frame-Options',
        expect.anything(),
      )
    })
  })

  describe('SSE safety', () => {
    it('does not read or buffer the response body', () => {
      // The middleware contract for streaming endpoints: no `await` on a
      // response, no `getReader`, no `pipe`, no read of the body. Assert the
      // middleware returns synchronously and only touches the response via
      // setResponseHeader (which is the ONLY h3 import the source uses).
      const event = { node: { res, req } } as unknown as Parameters<
        typeof securityHeadersMiddleware
      >[0]
      const result = securityHeadersMiddleware(event)
      expect(result).toBeUndefined()
      expect(setResponseHeaderMock).toHaveBeenCalled()
    })
  })
})
