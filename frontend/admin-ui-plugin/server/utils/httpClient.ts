import type { H3Event } from 'h3'
import { useBackendConfig } from './config'
import { useAuthConfig } from './config'
import { attachAuth, clearOidcSession, readSession, refreshTokens, writeSession } from './oidcSession'

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

function getRequestHeader(event: H3Event, name: string): string | undefined {
  const raw = event.node.req.headers?.[name.toLowerCase()]
  return Array.isArray(raw) ? raw[0] : raw
}

function errorIsUnauthorized(err: unknown): boolean {
  const status = (err as { response?: { status?: number } }).response?.status
  return status === 401 || status === 403
}

export async function proxyToBackend<T>(
  event: H3Event,
  path: string,
  options: { method?: string; body?: unknown; query?: Record<string, unknown> } = {},
): Promise<T> {
  const { baseUrl, apiKey, timeoutMs } = useBackendConfig()
  const authConfig = useAuthConfig()
  const url = `${baseUrl.replace(/\/$/, '')}${path}`
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  if (apiKey) headers['X-Api-Key'] = apiKey

  const inboundRequestId = getRequestHeader(event, 'x-request-id')
  const requestId = inboundRequestId || globalThis.crypto.randomUUID()
  headers['X-Request-Id'] = requestId
  const traceparent = getRequestHeader(event, 'traceparent')
  if (traceparent) headers.traceparent = traceparent
  const authorization = getRequestHeader(event, 'authorization')
  if (authorization) headers.Authorization = authorization
  const idempotencyKey = getRequestHeader(event, 'idempotency-key')
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey

  const correlationId = getRequestHeader(event, 'x-correlation-id')
  if (correlationId) headers['X-Correlation-Id'] = correlationId

  // Attach a Bearer token from the BFF session when OIDC is enabled and the
  // inbound request did not already provide an Authorization header.
  attachAuth(event, headers)

  const method = options.method ?? 'GET'
  const startedAt = Date.now()

  async function doFetch(extraHeaders?: Record<string, string>): Promise<T> {
    return (await $fetch<T>(url, {
      method,
      headers: { ...headers, ...extraHeaders },
      body: options.body,
      query: options.query,
      timeout: timeoutMs,
      retry: false,
      onRequest({ request }) {
        writeLog('info', `[BFF >] ${method} ${request}`, {
          requestId,
          headers: Object.keys({ ...headers, ...extraHeaders }),
        })
      },
      onResponse({ response }) {
        writeLog(
          'info',
          `[BFF <] ${method} ${url} ${response.status} ${Date.now() - startedAt}ms`,
          {
            requestId,
          },
        )
      },
      onResponseError({ response, error }) {
        writeLog('error', `[BFF !] ${method} ${url} ${response?.status ?? 'network'}`, {
          requestId,
          backendUrl: baseUrl,
          error: (error as Error | undefined)?.message,
        })
      },
    })) as T
  }

  try {
    return await doFetch()
  } catch (err: unknown) {
    const { refreshToken } = readSession(event)

    if (authConfig.enabled && refreshToken && errorIsUnauthorized(err)) {
      try {
        const refreshed = await refreshTokens(refreshToken)
        writeSession(event, refreshed, authConfig.callbackUrl)
        return await doFetch({ Authorization: `Bearer ${refreshed.access_token}` })
      } catch {
        clearOidcSession(event, authConfig.callbackUrl)
        // Fall through to surface the original backend error.
      }
    }

    const message = (err as Error | undefined)?.message ?? String(err)
    writeLog('error', `[BFF !] ${method} ${url} failed`, {
      requestId,
      backendUrl: baseUrl,
      error: message,
    })

    const fetchError = err as {
      name?: string
      cause?: { name?: string }
      response?: { status?: number }
      data?: { message?: string; code?: string; details?: unknown; diagnostics?: unknown }
    }
    if (fetchError.name === 'TimeoutError' || fetchError.cause?.name === 'TimeoutError') {
      throw createError({
        statusCode: 504,
        statusMessage: 'Backend request timed out',
        data: {
          code: 'BACKEND_TIMEOUT',
          message: 'Backend request timed out',
          backendUrl: baseUrl,
          originalError: message,
        },
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
      data: {
        message: data.message,
        code: data.code,
        details: data.details,
        diagnostics: data.diagnostics ?? null,
        backendUrl: baseUrl,
        originalError: message,
      },
    })
  }
}
