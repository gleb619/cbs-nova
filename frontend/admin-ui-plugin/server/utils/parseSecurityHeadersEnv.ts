/**
 * Parse the `NUXT_SECURITY_HEADERS` env var (T773).
 *
 * The same shape as `parseForwardedHeadersEnv` but accepts an extra
 * pseudo-value to keep deployments scriptable:
 *
 *   - unset / empty     → `undefined` (the middleware applies the default set)
 *   - `false` / `0`     → `false`     (the middleware is fully disabled)
 *   - JSON object       → `Record<string, string | null>` (override/add/remove)
 *
 * Like `parseForwardedHeadersEnv`, this runs in `module.ts#defaults` and
 * therefore at module-load time, so it must be sync and must not throw on
 * malformed input — the value is silently ignored and defaults apply. A
 * deployment that wants strict env validation can set
 * `adminUiPlugin.securityHeaders` in `nuxt.config.ts` instead.
 */
export type SecurityHeadersConfig = Record<string, string | null> | false

export function parseSecurityHeadersEnv(raw: string | undefined): SecurityHeadersConfig | undefined {
  if (raw === undefined) return undefined
  const trimmed = raw.trim()
  if (trimmed === '') return undefined
  if (trimmed === 'false' || trimmed === '0') return false

  try {
    const parsed = JSON.parse(trimmed) as unknown
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      // Coerce every value to string-or-null so a sloppy `{"X": true}` in
      // the env doesn't crash the builder later. Null is preserved so the
      // middleware can drop that header.
      const out: Record<string, string | null> = {}
      for (const [k, v] of Object.entries(parsed as Record<string, unknown>)) {
        if (v === null) {
          out[k] = null
        } else {
          out[k] = String(v)
        }
      }
      return out
    }
  } catch {
    // Fall through.
  }
  return undefined
}
