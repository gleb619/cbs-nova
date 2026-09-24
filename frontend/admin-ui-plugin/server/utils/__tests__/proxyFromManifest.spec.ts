import { beforeEach, describe, expect, it, type vi } from 'vitest'
import type { BffRouteSpec } from '../proxyFromManifest'
import { proxyFromManifest } from '../proxyFromManifest'

type HeaderMap = Record<string, string | undefined>
let headerMap: HeaderMap = {}

const makeEvent = (
  headers: HeaderMap = {},
  params: Record<string, string> = {},
  body?: unknown,
) => {
  headerMap = Object.fromEntries(Object.entries(headers).map(([k, v]) => [k.toLowerCase(), v]))
  const req: Record<string, unknown> = { headers: headerMap }
  if (body !== undefined) {
    req.body = body
    req.headers = { ...headerMap, 'content-type': 'application/json' }
  }
  return {
    method: body !== undefined ? 'POST' : 'GET',
    context: { params },
    node: { req },
  } as Parameters<typeof proxyFromManifest>[0]
}

const GET_SPEC: BffRouteSpec = {
  operationId: 'getExecution',
  method: 'GET',
  backendPath: '/api/executions/{id}',
  bffPath: '/api/v1/executions/{id}',
  params: ['id'],
}

const mockedFetch = () => $fetch as unknown as ReturnType<typeof vi.fn>

describe('proxyFromManifest', () => {
  beforeEach(() => {
    mockedFetch().mockClear()
  })

  describe('happy path', () => {
    it('interpolates route params into the backend URL and returns the body', async () => {
      const event = makeEvent({}, { id: 'run-42' })
      mockedFetch().mockResolvedValueOnce({ id: 'run-42', status: 'Completed' })

      const result = await proxyFromManifest<{ id: string; status: string }>(event, GET_SPEC)

      expect(result).toEqual({ id: 'run-42', status: 'Completed' })
      expect(mockedFetch()).toHaveBeenCalledTimes(1)
      const [url, opts] = mockedFetch().mock.calls[0] as [
        string,
        { method: string; headers: Record<string, string> },
      ]
      expect(url).toBe('http://localhost:8090/api/executions/run-42')
      expect(opts.method).toBe('GET')
      expect(opts.headers['Content-Type']).toBe('application/json')
      expect(opts.headers['X-Request-Id']).toMatch(
        /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/,
      )
    })

    it('forwards inbound x-request-id and authorization headers verbatim', async () => {
      const event = makeEvent(
        { 'x-request-id': 'rid-abc-123', authorization: 'Bearer abc.def.ghi' },
        { id: 'run-42' },
      )
      mockedFetch().mockResolvedValueOnce({})

      await proxyFromManifest(event, GET_SPEC)

      const [, opts] = mockedFetch().mock.calls[0] as [string, { headers: Record<string, string> }]
      expect(opts.headers['X-Request-Id']).toBe('rid-abc-123')
      expect(opts.headers.Authorization).toBe('Bearer abc.def.ghi')
    })

    it('passes POST method without a body for bodyless POST specs', async () => {
      const event = makeEvent()
      mockedFetch().mockResolvedValueOnce({ ok: true })
      const spec: BffRouteSpec = {
        operationId: 'cancelExecution',
        method: 'POST',
        backendPath: '/api/executions/{id}/cancel',
        bffPath: '/api/v1/executions/{id}/cancel',
        params: ['id'],
      }

      await proxyFromManifest(event, { ...spec, params: [] })

      const [url, opts] = mockedFetch().mock.calls[0] as [
        string,
        { method: string; body?: unknown },
      ]
      expect(url).toBe('http://localhost:8090/api/executions/{id}/cancel')
      expect(opts.method).toBe('POST')
      expect(opts.body).toBeUndefined()
    })

    it('reads and forwards the request body when hasBody is set', async () => {
      const event = makeEvent({}, {}, { name: 'draft-1', dsl: 'workflow X {}' })
      mockedFetch().mockResolvedValueOnce({ saved: true })
      const spec: BffRouteSpec = {
        operationId: 'saveDraft',
        method: 'POST',
        backendPath: '/api/dsl/drafts',
        bffPath: '/api/v1/dsl/drafts',
        params: [],
        hasBody: true,
      }

      await proxyFromManifest(event, spec)

      const [, opts] = mockedFetch().mock.calls[0] as [string, { method: string; body: unknown }]
      expect(opts.method).toBe('POST')
      expect(opts.body).toEqual({ name: 'draft-1', dsl: 'workflow X {}' })
    })

    it('still proxies GETs when explicitMethod is set', async () => {
      const event = makeEvent({}, { id: 'run-42' })
      mockedFetch().mockResolvedValueOnce({})
      const spec: BffRouteSpec = {
        ...GET_SPEC,
        operationId: 'readPublishHistoryEntry',
        explicitMethod: true,
      }

      await proxyFromManifest(event, spec)

      const [url, opts] = mockedFetch().mock.calls[0] as [string, { method: string }]
      expect(url).toBe('http://localhost:8090/api/executions/run-42')
      expect(opts.method).toBe('GET')
    })
  })

  describe('query params', () => {
    it('does NOT forward the inbound query string for plain-proxy specs (pinned quirk)', async () => {
      // proxyFromManifest/httpClient never read the inbound query, so generated
      // plain-proxy stubs silently drop query strings. Query-aware operations
      // are excluded from generation (see EXCLUDED_OPERATIONS in
      // scripts/generateBffRoutes.ts). Pinned here so the behavior is explicit.
      const event = makeEvent({}, { id: 'run-42' })
      mockedFetch().mockResolvedValueOnce({})

      await proxyFromManifest(event, GET_SPEC)

      const [, opts] = mockedFetch().mock.calls[0] as [string, { query?: Record<string, unknown> }]
      expect(opts.query).toBeUndefined()
    })
  })

  describe('missing param', () => {
    it('replaces a missing route param with an empty string and still proxies', async () => {
      const event = makeEvent()
      mockedFetch().mockResolvedValueOnce({})

      await proxyFromManifest(event, GET_SPEC)

      const [url] = mockedFetch().mock.calls[0] as [string]
      expect(url).toBe('http://localhost:8090/api/executions/')
    })
  })

  describe('missing manifest entry', () => {
    it('proxies any spec verbatim — there is no manifest lookup at this layer', async () => {
      // proxyFromManifest receives the spec object as an argument (the generated
      // stub embeds the manifest entry literally). An unknown operationId is
      // indistinguishable from a real one here; no 404/validation is raised.
      const event = makeEvent({}, { id: 'run-42' })
      mockedFetch().mockResolvedValueOnce({})

      await proxyFromManifest(event, { ...GET_SPEC, operationId: 'notInOpenapi' })

      const [url] = mockedFetch().mock.calls[0] as [string]
      expect(url).toBe('http://localhost:8090/api/executions/run-42')
    })
  })

  describe('backend unreachable', () => {
    it('maps a network failure to a 500 BACKEND_ERROR error envelope', async () => {
      const event = makeEvent({}, { id: 'run-42' })
      mockedFetch().mockRejectedValueOnce(new Error('network gone'))

      await expect(proxyFromManifest(event, GET_SPEC)).rejects.toMatchObject({
        statusCode: 500,
        statusMessage: 'Backend error',
        data: {
          message: 'Backend error',
          code: 'BACKEND_ERROR',
          details: null,
          diagnostics: null,
          backendUrl: 'http://localhost:8090',
          originalError: 'network gone',
        },
      })
    })

    it('passes through backend error status and data envelope when present', async () => {
      const event = makeEvent({}, { id: 'run-42' })
      const err = Object.assign(new Error('bad request'), {
        name: 'FetchError',
        response: { status: 404 },
        data: { message: 'Execution not found', code: 'NOT_FOUND', details: { id: 'run-42' } },
      })
      mockedFetch().mockRejectedValueOnce(err)

      await expect(proxyFromManifest(event, GET_SPEC)).rejects.toMatchObject({
        statusCode: 404,
        statusMessage: 'Execution not found',
        data: {
          message: 'Execution not found',
          code: 'NOT_FOUND',
          details: { id: 'run-42' },
          diagnostics: null,
          backendUrl: 'http://localhost:8090',
        },
      })
    })
  })
})
