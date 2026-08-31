import { beforeEach, describe, expect, it, vi } from 'vitest'
import { proxyToBackend } from '../httpClient'

type HeaderMap = Record<string, string | undefined>
let headerMap: HeaderMap = {}

const makeEvent = (headers: HeaderMap = {}) => {
  headerMap = Object.fromEntries(Object.entries(headers).map(([k, v]) => [k.toLowerCase(), v]))
  return { node: { req: { headers: headerMap } } } as Parameters<typeof proxyToBackend>[0]
}

const setRuntimeConfig = (overrides: Record<string, unknown> = {}) => {
  const merged = {
    backendBaseUrl: 'http://localhost:8090',
    backendApiKey: '',
    backendTimeoutMs: 10000,
    public: { appName: 'CBS Nova Admin' },
    ...overrides,
  }
  vi.mocked(useRuntimeConfig as never).mockReturnValue(
    merged as ReturnType<typeof useRuntimeConfig>,
  )
  vi.mocked(useBackendConfig as never).mockReturnValue({
    baseUrl: merged.backendBaseUrl as string,
    apiKey: merged.backendApiKey as string,
    timeoutMs: merged.backendTimeoutMs as number,
  })
}

describe('proxyToBackend', () => {
  beforeEach(() => {
    setRuntimeConfig()
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockClear()
  })

  it('returns body and sets Content-Type on success', async () => {
    const event = makeEvent()
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ ok: true, n: 1 })

    const result = await proxyToBackend<{ ok: boolean; n: number }>(event, '/api/foo')

    expect(result).toEqual({ ok: true, n: 1 })
    expect($fetch).toHaveBeenCalledTimes(1)
    const [url, opts] = ($fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      { method: string; headers: Record<string, string>; timeout: number },
    ]
    expect(url).toBe('http://localhost:8090/api/foo')
    expect(opts.method).toBe('GET')
    expect(opts.headers['Content-Type']).toBe('application/json')
    expect(opts.timeout).toBe(10000)
    expect(opts.headers['X-Request-Id']).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/,
    )
  })

  it('forwards X-Api-Key when apiKey is configured', async () => {
    setRuntimeConfig({ backendApiKey: 'secret-key' })
    const event = makeEvent()
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({})

    await proxyToBackend(event, '/api/foo')

    const [, opts] = ($fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      { headers: Record<string, string> },
    ]
    expect(opts.headers['X-Api-Key']).toBe('secret-key')
  })

  it('omits X-Api-Key when apiKey is empty', async () => {
    const event = makeEvent()
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({})

    await proxyToBackend(event, '/api/foo')

    const [, opts] = ($fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      { headers: Record<string, string> },
    ]
    expect(opts.headers['X-Api-Key']).toBeUndefined()
  })

  it('maps 4xx backend errors to createError with correct statusCode + data.code', async () => {
    const event = makeEvent()
    const err = Object.assign(new Error('bad request'), {
      name: 'FetchError',
      response: { status: 400 },
      data: { message: 'Invalid input', code: 'VALIDATION', details: { field: 'x' } },
    })
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockRejectedValueOnce(err)

    await expect(proxyToBackend(event, '/api/foo')).rejects.toMatchObject({
      statusCode: 400,
      statusMessage: 'Invalid input',
      data: { message: 'Invalid input', code: 'VALIDATION', details: { field: 'x' } },
    })
  })

  it('maps 5xx backend errors to createError with default code when absent', async () => {
    const event = makeEvent()
    const err = Object.assign(new Error('boom'), {
      name: 'FetchError',
      response: { status: 503 },
    })
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockRejectedValueOnce(err)

    await expect(proxyToBackend(event, '/api/foo')).rejects.toMatchObject({
      statusCode: 503,
      data: { code: 'BACKEND_ERROR' },
    })
  })

  it('falls back to status 500 when backend error has no response', async () => {
    const event = makeEvent()
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockRejectedValueOnce(
      new Error('network gone'),
    )

    await expect(proxyToBackend(event, '/api/foo')).rejects.toMatchObject({
      statusCode: 500,
      data: { code: 'BACKEND_ERROR' },
    })
  })

  it('maps ofetch TimeoutError (cause) to 504 BACKEND_TIMEOUT', async () => {
    const event = makeEvent()
    const cause = Object.assign(
      new Error('[TimeoutError]: The operation was aborted due to timeout'),
      {
        name: 'TimeoutError',
      },
    )
    const err = Object.assign(new Error('timeout wrapper'), {
      name: 'FetchError',
      cause,
    })
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockRejectedValueOnce(err)

    await expect(proxyToBackend(event, '/api/foo')).rejects.toMatchObject({
      statusCode: 504,
      statusMessage: 'Backend request timed out',
      data: { code: 'BACKEND_TIMEOUT', message: 'Backend request timed out' },
    })
  })

  it('maps a top-level TimeoutError to 504 BACKEND_TIMEOUT', async () => {
    const event = makeEvent()
    const err = Object.assign(new Error('[TimeoutError]'), { name: 'TimeoutError' })
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockRejectedValueOnce(err)

    await expect(proxyToBackend(event, '/api/foo')).rejects.toMatchObject({
      statusCode: 504,
      data: { code: 'BACKEND_TIMEOUT' },
    })
  })

  it('passes configured timeoutMs through to $fetch', async () => {
    setRuntimeConfig({ backendTimeoutMs: 2500 })
    const event = makeEvent()
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({})

    await proxyToBackend(event, '/api/foo')

    const [, opts] = ($fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      { timeout: number },
    ]
    expect(opts.timeout).toBe(2500)
  })

  it('generates and forwards x-request-id when inbound is absent', async () => {
    const event = makeEvent({})
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({})

    await proxyToBackend(event, '/api/foo')

    const [, opts] = ($fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      { headers: Record<string, string> },
    ]
    expect(opts.headers['X-Request-Id']).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/,
    )
  })

  it('forwards inbound x-request-id verbatim', async () => {
    const event = makeEvent({ 'x-request-id': 'rid-abc-123' })
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({})

    await proxyToBackend(event, '/api/foo')

    const [, opts] = ($fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      { headers: Record<string, string> },
    ]
    expect(opts.headers['X-Request-Id']).toBe('rid-abc-123')
  })

  it('forwards inbound traceparent header', async () => {
    const event = makeEvent({ traceparent: '00-aaa-bbb-01' })
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({})

    await proxyToBackend(event, '/api/foo')

    const [, opts] = ($fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      { headers: Record<string, string> },
    ]
    expect(opts.headers.traceparent).toBe('00-aaa-bbb-01')
  })

  it('omits traceparent header when inbound is absent', async () => {
    const event = makeEvent()
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({})

    await proxyToBackend(event, '/api/foo')

    const [, opts] = ($fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      { headers: Record<string, string> },
    ]
    expect(opts.headers.traceparent).toBeUndefined()
  })

  it('forwards inbound Authorization header verbatim', async () => {
    const event = makeEvent({ authorization: 'Bearer abc.def.ghi' })
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({})

    await proxyToBackend(event, '/api/foo')

    const [, opts] = ($fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      { headers: Record<string, string> },
    ]
    expect(opts.headers.Authorization).toBe('Bearer abc.def.ghi')
  })

  it('omits Authorization header when inbound is absent', async () => {
    const event = makeEvent()
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({})

    await proxyToBackend(event, '/api/foo')

    const [, opts] = ($fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      { headers: Record<string, string> },
    ]
    expect(opts.headers.Authorization).toBeUndefined()
  })

  it('forwards Authorization alongside X-Api-Key when both are present', async () => {
    setRuntimeConfig({ backendApiKey: 'secret-key' })
    const event = makeEvent({ authorization: 'Bearer abc.def.ghi' })
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({})

    await proxyToBackend(event, '/api/foo')

    const [, opts] = ($fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      { headers: Record<string, string> },
    ]
    expect(opts.headers.Authorization).toBe('Bearer abc.def.ghi')
    expect(opts.headers['X-Api-Key']).toBe('secret-key')
  })

  it('forwards POST method and body', async () => {
    const event = makeEvent()
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({})

    await proxyToBackend(event, '/api/foo', { method: 'POST', body: { x: 1 } })

    const [url, opts] = ($fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      { method: string; body: unknown },
    ]
    expect(url).toBe('http://localhost:8090/api/foo')
    expect(opts.method).toBe('POST')
    expect(opts.body).toEqual({ x: 1 })
  })

  it('strips trailing slash from baseUrl', async () => {
    setRuntimeConfig({ backendBaseUrl: 'http://localhost:8090/' })
    const event = makeEvent()
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({})

    await proxyToBackend(event, '/api/foo')

    const [url] = ($fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [string]
    expect(url).toBe('http://localhost:8090/api/foo')
  })

  it('forwards inbound Idempotency-Key header verbatim', async () => {
    const event = makeEvent({ 'idempotency-key': 'idem-abc' })
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({})

    await proxyToBackend(event, '/api/foo')

    const [, opts] = ($fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      { headers: Record<string, string> },
    ]
    expect(opts.headers['Idempotency-Key']).toBe('idem-abc')
  })

  it('omits Idempotency-Key header when inbound is absent', async () => {
    const event = makeEvent()
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({})

    await proxyToBackend(event, '/api/foo')

    const [, opts] = ($fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      { headers: Record<string, string> },
    ]
    expect(opts.headers['Idempotency-Key']).toBeUndefined()
  })

  it('forwards inbound X-Correlation-Id header verbatim', async () => {
    const event = makeEvent({ 'x-correlation-id': 'corr-abc' })
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({})

    await proxyToBackend(event, '/api/foo')

    const [, opts] = ($fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      { headers: Record<string, string> },
    ]
    expect(opts.headers['X-Correlation-Id']).toBe('corr-abc')
  })

  it('omits X-Correlation-Id header when inbound is absent', async () => {
    const event = makeEvent()
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({})

    await proxyToBackend(event, '/api/foo')

    const [, opts] = ($fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      { headers: Record<string, string> },
    ]
    expect(opts.headers['X-Correlation-Id']).toBeUndefined()
  })
})
