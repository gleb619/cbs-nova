import type { H3Event } from 'h3'
import { createError, getHeader } from 'h3'
import { $fetch } from 'ofetch'
import { useBackendConfig } from './config'

export async function proxyToBackend<T>(
  event: H3Event,
  path: string,
  options: { method?: string; body?: unknown; query?: Record<string, unknown> } = {},
): Promise<T> {
  const { baseUrl, apiKey, timeoutMs } = useBackendConfig()
  const url = `${baseUrl.replace(/\/$/, '')}${path}`
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  if (apiKey) headers['X-Api-Key'] = apiKey

  // Propagate trace context. Always generate a request id so backend logs can
  // be correlated with BFF logs even when the client didn't send one.
  // getHeader/createError are auto-imported by Nitro at runtime.
  const inboundRequestId = getHeader(event, 'x-request-id')
  const requestId = inboundRequestId || globalThis.crypto.randomUUID()
  headers['X-Request-Id'] = requestId
  const traceparent = getHeader(event, 'traceparent')
  if (traceparent) headers.traceparent = traceparent

  try {
    return (await $fetch<T>(url, {
      method: options.method ?? 'GET',
      headers,
      body: options.body,
      query: options.query,
      timeout: timeoutMs,
    })) as T
  } catch (err: unknown) {
    // ofetch wraps timeout abort errors in a FetchError whose cause has
    // name === 'TimeoutError'. Map to a 504 with a stable code so the
    // frontend can distinguish timeouts from upstream 5xxs.
    const fetchError = err as {
      name?: string
      cause?: { name?: string }
      response?: { status?: number }
      data?: { message?: string; code?: string; details?: unknown }
    }
    if (fetchError.name === 'TimeoutError' || fetchError.cause?.name === 'TimeoutError') {
      throw createError({
        statusCode: 504,
        statusMessage: 'Backend request timed out',
        data: { code: 'BACKEND_TIMEOUT', message: 'Backend request timed out' },
      })
    }
    const status = fetchError.response?.status ?? 500
    const data = fetchError.data ?? {
      message: 'Backend error',
      code: 'BACKEND_ERROR',
      details: null,
    }
    throw createError({
      statusCode: status,
      statusMessage: data.message ?? 'Backend error',
      data: { message: data.message, code: data.code, details: data.details },
    })
  }
}
