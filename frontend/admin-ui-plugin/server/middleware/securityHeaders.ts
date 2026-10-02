/**
 * Nitro middleware — attach conservative response security headers (T773).
 *
 * Registered automatically: Nitro's file scanner picks up every `*.ts` in
 * `server/middleware/`, and the module's `nitro:config` hook (in `module.ts`)
 * adds `server/` to `scanDirs`. There is no explicit registration in
 * `module.ts` — adding this file is enough.
 *
 * Hard rules for this file:
 *   1. NEVER touch the response body. This middleware must be safe for SSE
 *      streams (`/api/v1/executions/:id/events`) and for the `Content-Type:
 *      text/event-stream` `executions/export.get.ts` log tail. That is why
 *      there is no `await nextEvent` / no response interceptor — only
 *      `setResponseHeader` is called, which is metadata-only.
 *   2. NEVER clobber a header the host app (or an earlier middleware) has
 *      already set. If `event.node.res.getHeader(name)` returns a non-undefined
 *      value, the middleware leaves it alone unless the module option
 *      `adminUiPlugin.securityHeaders.overrides` explicitly says to override
 *      it. The override check is done by `buildSecurityHeaders` itself.
 *   3. The layer is opt-out: `adminUiPlugin.securityHeaders: false` disables
 *      every header this middleware would otherwise attach.
 */
import { setResponseHeader } from 'h3'
// `useRuntimeConfig` is provided as a Nitro global — do not import it from
// `#imports` (that virtual module is only available inside the Nuxt app, not
// in Nitro server runtime, and would break unit specs that resolve imports
// outside the Vitest Nuxt stub).
import { buildSecurityHeaders } from '../utils/securityHeaders'

export default defineEventHandler((event) => {
  const config = useRuntimeConfig() as {
    securityHeaders?: Record<string, string | null> | false
  }
  // `undefined` is the "use defaults" case; only an explicit `false` disables.
  if (config.securityHeaders === false) {
    return
  }

  const isHttps = isRequestHttps(event)
  const headers = buildSecurityHeaders({
    overrides: config.securityHeaders,
    isHttps,
  })

  const overrides = typeof config.securityHeaders === 'object' ? config.securityHeaders : undefined

  for (const [name, value] of Object.entries(headers)) {
    // Respect pre-existing response headers (host app, reverse proxy, earlier middleware) unless
    // the module option explicitly overrides this header with a string value.
    const explicitOverride = typeof overrides?.[name] === 'string'
    if (!explicitOverride && event.node.res.getHeader(name) !== undefined) {
      continue
    }
    setResponseHeader(event, name, value)
  }
})

/**
 * Detect HTTPS on the inbound request without trusting `req.protocol` alone
 * (it is derived from `req.socket.encrypted` and is `http` behind most TLS-
 * terminating proxies). Fall back to `X-Forwarded-Proto: https` so deployments
 * behind a reverse proxy still get HSTS. Only the first hop is consulted —
 * trusting a comma-separated chain would let a client spoof the scheme.
 */
function isRequestHttps(event: Parameters<Parameters<typeof defineEventHandler>[0]>[0]): boolean {
  if (event.node.req.socket?.encrypted) {
    return true
  }
  const forwarded = event.node.req.headers['x-forwarded-proto']
  const first = Array.isArray(forwarded) ? forwarded[0] : forwarded
  return typeof first === 'string' && first.toLowerCase().startsWith('https')
}
