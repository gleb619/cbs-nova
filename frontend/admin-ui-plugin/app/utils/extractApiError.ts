/**
 * Shared extraction of the BFF error envelope.
 *
 * The Nitro BFF (`proxyToBackend`) throws `createError({ statusCode,
 * statusMessage, data: { message, code, details, diagnostics } })`, so the
 * backend's real message plus machine-readable fields live under `err.data.*`.
 * A bare `(err as Error).message` only yields the generic `statusMessage` and
 * silently drops them. This util normalises h3 `createError` objects, ofetch
 * `FetchError`s, plain `Error`s and strings into a single {@link ApiError}
 * shape. It never throws.
 */

export interface ApiError {
  /** Human-readable message, resolved from the richest source available. */
  message: string
  /** Machine-readable backend error code (`err.data.code`). */
  code?: string
  /** HTTP status (`err.statusCode` for h3, `err.response.status` for ofetch). */
  status?: number
  /** Pass-through of `err.data.diagnostics`. */
  diagnostics?: unknown
  /** Pass-through of `err.data.details`. */
  details?: unknown
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

export function extractApiError(err: unknown, fallback = 'Request failed'): ApiError {
  try {
    const record = isRecord(err) ? err : undefined
    const data = isRecord(record?.data) ? record.data : undefined

    const dataMessage = typeof data?.message === 'string' ? data.message : undefined
    const statusMessage = typeof record?.statusMessage === 'string' ? record.statusMessage : undefined
    const recordMessage = typeof record?.message === 'string' ? record.message : undefined
    const primitiveMessage =
      typeof err === 'string' || typeof err === 'number' || typeof err === 'boolean'
        ? String(err)
        : undefined

    const message = dataMessage ?? statusMessage ?? recordMessage ?? primitiveMessage ?? fallback

    const code = typeof data?.code === 'string' ? data.code : undefined

    const statusCode = typeof record?.statusCode === 'number' ? record.statusCode : undefined
    const response = isRecord(record?.response) ? record.response : undefined
    const responseStatus = typeof response?.status === 'number' ? response.status : undefined

    return {
      message,
      ...(code !== undefined ? { code } : {}),
      ...(statusCode !== undefined || responseStatus !== undefined
        ? { status: statusCode ?? responseStatus }
        : {}),
      ...(data?.diagnostics !== undefined ? { diagnostics: data.diagnostics } : {}),
      ...(data?.details !== undefined ? { details: data.details } : {}),
    }
  } catch {
    // Defensive: extraction must never mask the original failure.
    return { message: fallback }
  }
}
