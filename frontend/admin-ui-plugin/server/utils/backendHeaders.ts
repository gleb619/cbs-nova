import type { H3Event } from 'h3'
import { useBackendConfig } from './config'

/**
 * Inbound (lowercase) → outbound (canonical) header-name allowlist for the
 * BFF → backend pass-through. One place to add a forwarded header.
 */
export const FORWARDED_HEADERS: Record<string, string> = {
  traceparent: 'traceparent',
  authorization: 'Authorization',
  'idempotency-key': 'Idempotency-Key',
  'x-correlation-id': 'X-Correlation-Id',
}

// follow-up: move this allowlist into runtimeConfig so it can be tuned per
// environment without a code change. Skipped here to keep this PR small.

/** Array-safe inbound header accessor — unwraps string[] to its first value. */
function getRequestHeader(event: H3Event, name: string): string | undefined {
  const raw = event.node.req.headers?.[name.toLowerCase()]
  return Array.isArray(raw) ? raw[0] : raw
}

export interface BuildBackendHeadersOptions {
  /** When false, omit `Content-Type: application/json`. Default true. */
  json?: boolean
}

export interface BuildBackendHeadersResult {
  headers: Record<string, string>
  /** X-Request-Id used (passthrough or generated). */
  requestId: string
  /** Inbound X-Correlation-Id, if present — for caller logging. */
  correlationId?: string
}

/**
 * Assemble the request headers the BFF sends to Spring Boot.
 *
 * Does NOT call `attachAuth`: that helper mutates the header object in place
 * and is refresh-aware (proxyToBackend re-invokes it with a refreshed
 * bearer on 401 retries), so callers must invoke `attachAuth(event, headers)`
 * themselves after this util returns.
 */
export function buildBackendHeaders(
  event: H3Event,
  opts: BuildBackendHeadersOptions = {},
): BuildBackendHeadersResult {
  const { apiKey } = useBackendConfig()
  const includeJson = opts.json !== false

  const headers: Record<string, string> = {}
  if (includeJson) headers['Content-Type'] = 'application/json'
  if (apiKey) headers['X-Api-Key'] = apiKey

  const inboundRequestId = getRequestHeader(event, 'x-request-id')
  const requestId = inboundRequestId || globalThis.crypto.randomUUID()
  headers['X-Request-Id'] = requestId

  for (const [inboundName, outboundName] of Object.entries(FORWARDED_HEADERS)) {
    const value = getRequestHeader(event, inboundName)
    if (value) headers[outboundName] = value
  }

  const correlationId = getRequestHeader(event, 'x-correlation-id')

  return { headers, requestId, correlationId }
}
