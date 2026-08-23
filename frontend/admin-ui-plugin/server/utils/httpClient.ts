import { useBackendConfig } from './config'

type LogLevel = 'debug' | 'info' | 'warn' | 'error'

const LOG_LEVEL = (typeof process !== 'undefined' && (process.env.LOG_LEVEL as LogLevel)) || 'debug'
const LEVEL_RANK: Record<LogLevel, number> = {
  debug: 0,
  info: 1,
  warn: 2,
  error: 3,
}

function isLogEnabled(level: LogLevel): boolean {
  return LEVEL_RANK[level] >= (LEVEL_RANK[LOG_LEVEL] ?? LEVEL_RANK.debug)
}

function writeLog(level: LogLevel, message: string, data?: Record<string, unknown>): void {
  if (!isLogEnabled(level)) return
  const fn = console[level] as (msg: string, ...rest: unknown[]) => void
  if (data) {
    fn(message, data)
    return
  }
  fn(message)
}

function getRequestHeader(
  event: { node?: { req?: { headers?: Record<string, string | string[] | undefined> } } },
  name: string,
): string | undefined {
  const raw = event.node?.req?.headers?.[name.toLowerCase()]
  return Array.isArray(raw) ? raw[0] : raw
}

export async function proxyToBackend<T>(
  event: { node?: { req?: { headers?: Record<string, string | string[] | undefined> } } },
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
  const inboundRequestId = getRequestHeader(event, 'x-request-id')
  const requestId = inboundRequestId || globalThis.crypto.randomUUID()
  headers['X-Request-Id'] = requestId
  const traceparent = getRequestHeader(event, 'traceparent')
  if (traceparent) headers.traceparent = traceparent

  const method = options.method ?? 'GET'
  const startedAt = Date.now()

  try {
    return (await $fetch<T>(url, {
      method,
      headers,
      body: options.body,
      query: options.query,
      timeout: timeoutMs,
      onRequest({ request }) {
        writeLog('debug', `[BFF >] ${method} ${request}`, {
          requestId,
          headers: Object.keys(headers),
        })
      },
      onResponse({ response }) {
        writeLog(
          'info',
          `[BFF <] ${method} ${path} ${response.status} ${Date.now() - startedAt}ms`,
          {
            requestId,
          },
        )
      },
      onResponseError({ response, error }) {
        writeLog('error', `[BFF !] ${method} ${path} ${response?.status ?? 'network'}`, {
          requestId,
          error: (error as Error | undefined)?.message,
        })
      },
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
