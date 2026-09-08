import { beforeEach, describe, expect, it, vi } from 'vitest'
import { buildBackendHeaders } from '../backendHeaders'

type HeaderMap = Record<string, string | undefined>
let headerMap: HeaderMap = {}

const makeEvent = (headers: HeaderMap = {}) => {
  headerMap = Object.fromEntries(Object.entries(headers).map(([k, v]) => [k.toLowerCase(), v]))
  return {
    node: { req: { headers: headerMap } },
  } as Parameters<typeof buildBackendHeaders>[0]
}

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

const setApiKey = (value: string) => {
  vi.mocked(useRuntimeConfig as never).mockReturnValue({
    backendBaseUrl: 'http://localhost:8090',
    backendApiKey: value,
    backendTimeoutMs: 10000,
  })
}

describe('buildBackendHeaders', () => {
  beforeEach(() => {
    setApiKey('')
  })

  describe('Content-Type', () => {
    it('sets Content-Type: application/json by default', () => {
      const event = makeEvent()
      const { headers } = buildBackendHeaders(event)
      expect(headers['Content-Type']).toBe('application/json')
    })

    it('sets Content-Type: application/json when opts.json is true', () => {
      const event = makeEvent()
      const { headers } = buildBackendHeaders(event, { json: true })
      expect(headers['Content-Type']).toBe('application/json')
    })

    it('omits Content-Type when opts.json is false', () => {
      const event = makeEvent()
      const { headers } = buildBackendHeaders(event, { json: false })
      expect(headers['Content-Type']).toBeUndefined()
    })
  })

  describe('X-Api-Key', () => {
    it('forwards X-Api-Key when apiKey is configured', () => {
      setApiKey('secret-key')
      const event = makeEvent()
      const { headers } = buildBackendHeaders(event)
      expect(headers['X-Api-Key']).toBe('secret-key')
    })

    it('omits X-Api-Key when apiKey is empty', () => {
      const event = makeEvent()
      const { headers } = buildBackendHeaders(event)
      expect(headers['X-Api-Key']).toBeUndefined()
    })
  })

  describe('X-Request-Id', () => {
    it('forwards inbound x-request-id verbatim and returns it', () => {
      const event = makeEvent({ 'x-request-id': 'rid-abc-123' })
      const { headers, requestId } = buildBackendHeaders(event)
      expect(headers['X-Request-Id']).toBe('rid-abc-123')
      expect(requestId).toBe('rid-abc-123')
    })

    it('generates a UUID when inbound x-request-id is absent', () => {
      const event = makeEvent()
      const { headers, requestId } = buildBackendHeaders(event)
      expect(headers['X-Request-Id']).toMatch(UUID_RE)
      expect(requestId).toBe(headers['X-Request-Id'])
    })
  })

  describe('FORWARDED_HEADERS pass-through', () => {
    it('forwards inbound traceparent', () => {
      const event = makeEvent({ traceparent: '00-aaa-bbb-01' })
      const { headers } = buildBackendHeaders(event)
      expect(headers.traceparent).toBe('00-aaa-bbb-01')
    })

    it('omits traceparent when inbound is absent', () => {
      const event = makeEvent()
      const { headers } = buildBackendHeaders(event)
      expect(headers.traceparent).toBeUndefined()
    })

    it('forwards inbound authorization as Authorization', () => {
      const event = makeEvent({ authorization: 'Bearer abc.def.ghi' })
      const { headers } = buildBackendHeaders(event)
      expect(headers.Authorization).toBe('Bearer abc.def.ghi')
    })

    it('omits Authorization when inbound is absent', () => {
      const event = makeEvent()
      const { headers } = buildBackendHeaders(event)
      expect(headers.Authorization).toBeUndefined()
    })

    it('forwards inbound idempotency-key as Idempotency-Key', () => {
      const event = makeEvent({ 'idempotency-key': 'idem-abc' })
      const { headers } = buildBackendHeaders(event)
      expect(headers['Idempotency-Key']).toBe('idem-abc')
    })

    it('omits Idempotency-Key when inbound is absent', () => {
      const event = makeEvent()
      const { headers } = buildBackendHeaders(event)
      expect(headers['Idempotency-Key']).toBeUndefined()
    })

    it('forwards inbound x-correlation-id as X-Correlation-Id and returns it', () => {
      const event = makeEvent({ 'x-correlation-id': 'corr-abc' })
      const { headers, correlationId } = buildBackendHeaders(event)
      expect(headers['X-Correlation-Id']).toBe('corr-abc')
      expect(correlationId).toBe('corr-abc')
    })

    it('omits X-Correlation-Id when inbound is absent and returns undefined', () => {
      const event = makeEvent()
      const { headers, correlationId } = buildBackendHeaders(event)
      expect(headers['X-Correlation-Id']).toBeUndefined()
      expect(correlationId).toBeUndefined()
    })
  })

  describe('array-safe header access', () => {
    it('unwraps string[] inbound header to its first value', () => {
      const event = makeEvent({ traceparent: '00-aaa-bbb-01' })
      headerMap.traceparent = ['00-aaa-bbb-01', 'second-value-ignored']
      const { headers } = buildBackendHeaders(event)
      expect(headers.traceparent).toBe('00-aaa-bbb-01')
    })
  })
})
