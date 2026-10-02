/**
 * Parse `BACKEND_FORWARDED_HEADERS` env var as a JSON object. Returns
 * `undefined` when unset or empty so the merge in `resolveRuntimeConfig`
 * falls back to whatever default the host / caller provided. Throws on
 * malformed JSON — fail closed, surfacing the misconfiguration rather
 * than silently dropping the operator's intent.
 */
export function parseForwardedHeadersEnv(
  raw: string | undefined,
): Record<string, string> | undefined {
  if (!raw) return undefined
  const parsed: unknown = JSON.parse(raw)
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(
      '[admin-ui-plugin] BACKEND_FORWARDED_HEADERS must be a JSON object of ' +
        '{ inbound: outbound } header-name pairs (e.g. \'{"x-tenant-id":"X-Tenant-Id"}\').',
    )
  }
  const out: Record<string, string> = {}
  for (const [k, v] of Object.entries(parsed as Record<string, unknown>)) {
    if (typeof v !== 'string') {
      throw new Error(
        `[admin-ui-plugin] BACKEND_FORWARDED_HEADERS.${k} must map to a string ` +
          `(outbound header name), got ${typeof v}.`,
      )
    }
    out[k] = v
  }
  return out
}
