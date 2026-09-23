import { type H3Event, type HTTPMethod, setResponseHeader, setResponseStatus } from 'h3'
import { buildBackendHeaders } from './backendHeaders'
import { useAuthConfig, useBackendConfig } from './config'
import {
  attachAuth,
  clearOidcSession,
  detectRefreshReuse,
  readSession,
  refreshTokens,
  sessionExpiredAbsolute,
  writeSession,
} from './oidcSession'

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

function errorIsUnauthorized(err: unknown): boolean {
  const status = (err as { response?: { status?: number } }).response?.status
  return status === 401 || status === 403
}

export interface ProxyOptions {
  method?: string
  body?: unknown
  query?: Record<string, unknown>
  raw?: boolean
  forwardResponseHeaders?: string[]
}

export async function proxyToBackend<T>(
  event: H3Event,
  path: string,
  options: ProxyOptions = {},
): Promise<T> {
  const { baseUrl, timeoutMs } = useBackendConfig()
  const authConfig = useAuthConfig()
  const url = `${baseUrl.replace(/\/$/, '')}${path}`
  const { headers, requestId, correlationId } = buildBackendHeaders(event, { json: !options.raw })

  // Attach a Bearer token from the BFF session when OIDC is enabled and the
  // inbound request did not already provide an Authorization header.
  attachAuth(event, headers)

  const method = (options.method ?? 'GET') as HTTPMethod | Lowercase<HTTPMethod>
  const startedAt = Date.now()
  const isRaw = options.raw === true

  async function doFetch(extraHeaders?: Record<string, string>): Promise<T> {
    const fetchHeaders = { ...headers, ...extraHeaders }
    if (isRaw) {
      const res = await $fetch.raw<string>(url, {
        method,
        headers: fetchHeaders,
        body: options.body as never,
        query: options.query,
        responseType: 'text',
        timeout: timeoutMs,
        retry: false,
        onRequest({ request }) {
          writeLog('info', `[BFF >] ${method} ${request}`, {
            requestId,
            correlationId,
            headers: Object.keys(fetchHeaders),
          })
        },
        onResponse({ response }) {
          writeLog(
            'info',
            `[BFF <] ${method} ${url} ${response.status} ${Date.now() - startedAt}ms`,
            {
              requestId,
              correlationId,
            },
          )
        },
        onResponseError({ response, error }) {
          writeLog('error', `[BFF !] ${method} ${url} ${response?.status ?? 'network'}`, {
            requestId,
            correlationId,
            backendUrl: baseUrl,
            error: (error as Error | undefined)?.message,
          })
        },
      })
      setResponseStatus(event, res.status)
      for (const name of options.forwardResponseHeaders ?? []) {
        const value = res.headers.get(name)
        if (value) setResponseHeader(event, name, value)
      }
      return res._data as T
    }

    return (await $fetch<T>(url, {
      method,
      headers: fetchHeaders,
      body: options.body as never,
      query: options.query,
      timeout: timeoutMs,
      retry: false,
      onRequest({ request }) {
        writeLog('info', `[BFF >] ${method} ${request}`, {
          requestId,
          correlationId,
          headers: Object.keys(fetchHeaders),
        })
      },
      onResponse({ response }) {
        writeLog(
          'info',
          `[BFF <] ${method} ${url} ${response.status} ${Date.now() - startedAt}ms`,
          {
            requestId,
            correlationId,
          },
        )
      },
      onResponseError({ response, error }) {
        writeLog('error', `[BFF !] ${method} ${url} ${response?.status ?? 'network'}`, {
          requestId,
          correlationId,
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

    // Absolute-timeout enforcement: if the wall-clock session age
    // exceeds the configured limit, clear immediately and fall through
    // to the original backend error (the user is logged out either way).
    if (authConfig.enabled && sessionExpiredAbsolute(event)) {
      clearOidcSession(event, authConfig.callbackUrl)
    } else if (authConfig.enabled && refreshToken && errorIsUnauthorized(err)) {
      // Refresh-token reuse detection. A replay of the previously
      // rotated-out refresh token (within the RT_PREV_COOKIE marker
      // window) is treated as a stolen-token signal: clear the
      // session and fail the request with a 401 so the client knows
      // to drop credentials.
      if (authConfig.sessionRotateOnRefresh && (await detectRefreshReuse(event, refreshToken))) {
        clearOidcSession(event, authConfig.callbackUrl)
        console.warn('[auth] refresh-token reuse detected — session cleared')
        throw createError({
          statusCode: 401,
          statusMessage: 'session revoked',
        })
      }
      try {
        const refreshed = await refreshTokens(refreshToken)
        await writeSession(event, refreshed, authConfig.callbackUrl, { isRefresh: true })
        return await doFetch({ Authorization: `Bearer ${refreshed.access_token}` })
      } catch {
        clearOidcSession(event, authConfig.callbackUrl)
        // Fall through to surface the original backend error.
      }
    }

    const message = (err as Error | undefined)?.message ?? String(err)
    writeLog('error', `[BFF !] ${method} ${url} failed`, {
      requestId,
      correlationId,
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
