/**
 * Pure builder for BFF response security headers (T773).
 *
 * Lives in `server/utils/` so it is auto-imported by Nitro AND unit-testable
 * without spinning up a Nitro app. The Nitro middleware that actually attaches
 * the headers to outgoing responses is in `server/middleware/securityHeaders.ts`.
 *
 * Why a separate util? Two reasons:
 *   1. The header policy is non-trivial (HSTS-conditional, opt-out, per-header
 *      override/remove) and deserves a spec that does not depend on h3.
 *   2. The middleware itself is one `for` loop; the interesting logic is the
 *      "which headers, in which conditions" decision and that is what
 *      regresses in practice.
 *
 * The util never touches `event.node.req.res` or any global — everything it
 * needs is passed in. That keeps SSE streams (executions events, log tail)
 * safe: the middleware only calls `setResponseHeader` and never reads or
 * buffers the body, so streaming responses are unchanged.
 */

export interface BuildSecurityHeadersOptions {
  /**
   * `false` ⇒ return `{}` (the layer is fully disabled).
   * Object ⇒ per-header override/add:
   *   - `{ 'X-Frame-Options': 'DENY' }` ⇒ replace the default value
   *   - `{ 'X-Frame-Options': null }`   ⇒ remove that header from the defaults
   *   - `{ 'X-Custom': 'value' }`       ⇒ add a new header to the response
   * `undefined` ⇒ use defaults as-is.
   */
  overrides?: Record<string, string | null> | false
  /**
   * When true, add `Strict-Transport-Security`. The middleware derives this
   * from the inbound request (TLS or `X-Forwarded-Proto: https`); this util
   * does not sniff the transport itself, it trusts the caller.
   */
  isHttps?: boolean
}

/** Headers the BFF attaches to every response by default. */
export const DEFAULT_SECURITY_HEADERS: Readonly<Record<string, string>> = Object.freeze({
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'strict-origin-when-cross-origin',
  'X-Frame-Options': 'SAMEORIGIN',
  // Deny sensitive browser APIs that the admin UI does not need. Keep the
  // policy narrow so adding a future feature (e.g. voice input) only needs a
  // header tweak, not a CSP rewrite.
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
})

/** HSTS value applied when the request was received over HTTPS. */
export const HSTS_HEADER_VALUE = 'max-age=31536000; includeSubDomains'

/**
 * Build the final response header set. Pure function — no h3, no event.
 *
 * Resolution order:
 *   1. If `overrides === false` ⇒ return `{}` (layer disabled, no headers).
 *   2. Start from `DEFAULT_SECURITY_HEADERS`.
 *   3. Apply `overrides`:
 *      - `null`  ⇒ remove the header
 *      - string  ⇒ replace the value
 *      - new key ⇒ add the header
 *   4. If `isHttps`, add/override `Strict-Transport-Security`.
 *
 * Returns a fresh object every call so the middleware can iterate it
 * without worrying about callers mutating shared state.
 */
export function buildSecurityHeaders(
  options: BuildSecurityHeadersOptions = {},
): Record<string, string> {
  const { overrides, isHttps = false } = options

  if (overrides === false) {
    return {}
  }

  const headers: Record<string, string> = { ...DEFAULT_SECURITY_HEADERS }

  if (overrides) {
    for (const [name, value] of Object.entries(overrides)) {
      if (value === null) {
        delete headers[name]
      } else {
        headers[name] = value
      }
    }
  }

  if (isHttps) {
    headers['Strict-Transport-Security'] = HSTS_HEADER_VALUE
  }

  return headers
}
