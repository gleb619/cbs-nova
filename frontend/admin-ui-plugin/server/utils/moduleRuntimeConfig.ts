import type { ModuleOptions } from '../../module'

// ---------------------------------------------------------------------------
// Pure runtimeConfig merge (T468).
//
// `module.ts` calls this instead of inlining `||` chains: Nuxt may already
// have populated `runtimeConfig.X` from `NUXT_*` env vars, and `||` cannot
// tell "unset" apart from an explicit falsy-but-valid value (`0`, `false`,
// `''`). Nullish coalescing (`??`) resolves that: only null/undefined fall
// through. This matches the read side (`server/utils/config.ts`), which
// already uses `??` semantics.
// ---------------------------------------------------------------------------

/** Server-side runtime config keys owned by the admin UI module. */
export interface AdminUiRuntimeConfig {
  backendBaseUrl: string
  backendApiKey: string
  backendTimeoutMs: number
  /**
   * Inbound (lowercase) → outbound (canonical) header-name allowlist for
   * the BFF → backend pass-through. `undefined` ⇒ `buildBackendHeaders`
   * falls back to its built-in default set so out-of-the-box behaviour is
   * unchanged.
   */
  backendForwardedHeaders?: Record<string, string>
  authIssuer: string
  authClientId: string
  authClientSecret: string
  authCallbackUrl: string
  authPostLogoutRedirect: string
  authSessionIdleTimeoutSeconds: number
  authSessionAbsoluteTimeoutSeconds: number
  authSessionSecureCookies: string | boolean
  authSessionRotateOnRefresh: string
}

/** Public runtime config keys owned by the admin UI module. */
export interface AdminUiPublicRuntimeConfig {
  authEnabled: boolean
  appName: string
  temporalUiBaseUrl: string
  temporalNamespace: string
}

/**
 * Shape of the host app's `runtimeConfig` as seen by the merge — every key
 * optional (Nuxt fills them from env vars / host config before the module
 * runs) and `public` possibly absent. `authSessionSecureCookies` may arrive
 * as a boolean from host config or a string ('true'/'false'/'') from env.
 */
export interface ExistingRuntimeConfig {
  backendBaseUrl?: string
  backendApiKey?: string
  backendTimeoutMs?: number
  backendForwardedHeaders?: Record<string, string>
  authIssuer?: string
  authClientId?: string
  authClientSecret?: string
  authCallbackUrl?: string
  authPostLogoutRedirect?: string
  authSessionIdleTimeoutSeconds?: number
  authSessionAbsoluteTimeoutSeconds?: number
  authSessionSecureCookies?: string | boolean
  authSessionRotateOnRefresh?: string
  public?: {
    authEnabled?: boolean
    appName?: string
    temporalUiBaseUrl?: string
    temporalNamespace?: string
  }
}

/**
 * Merge existing (env/host-populated) runtimeConfig with module options and
 * hardcoded defaults, `existing.X ?? options.X ?? DEFAULT` for every key.
 *
 * Precedence: existing value (already on `nuxt.options.runtimeConfig`) wins,
 * then the module option, then the default. Explicit falsy values (`0`,
 * `false`, `''`) win over defaults — that is the one intentional behaviour
 * change vs the old `||` merge.
 *
 * `authEnabled` is derived: `existing.public.authEnabled ?? Boolean(issuer)`
 * so the Sign-in affordance renders only when OIDC is configured, unless the
 * host explicitly overrides the flag.
 */
export function resolveRuntimeConfig(
  existing: ExistingRuntimeConfig,
  options: ModuleOptions,
): { config: AdminUiRuntimeConfig; publicConfig: AdminUiPublicRuntimeConfig } {
  const config: AdminUiRuntimeConfig = {
    backendBaseUrl: existing.backendBaseUrl ?? options.backendBaseUrl ?? 'http://localhost:8090',
    backendApiKey: existing.backendApiKey ?? options.backendApiKey ?? '',
    backendTimeoutMs: existing.backendTimeoutMs ?? options.backendTimeoutMs ?? 10000,
    // No built-in default: `buildBackendHeaders` falls back to its own
    // DEFAULT_FORWARDED_HEADERS when this is undefined, so omitting the
    // option keeps the historical four-entry allowlist.
    backendForwardedHeaders: existing.backendForwardedHeaders ?? options.backendForwardedHeaders,
    authIssuer: existing.authIssuer ?? options.authIssuer ?? '',
    authClientId: existing.authClientId ?? options.authClientId ?? 'cbs-nova-bff',
    authClientSecret: existing.authClientSecret ?? options.authClientSecret ?? '',
    authCallbackUrl:
      existing.authCallbackUrl ??
      options.authCallbackUrl ??
      'http://localhost:3000/api/v1/auth/callback',
    authPostLogoutRedirect:
      existing.authPostLogoutRedirect ?? options.authPostLogoutRedirect ?? '/',
    authSessionIdleTimeoutSeconds:
      existing.authSessionIdleTimeoutSeconds ?? options.authSessionIdleTimeoutSeconds ?? 0,
    authSessionAbsoluteTimeoutSeconds:
      existing.authSessionAbsoluteTimeoutSeconds ?? options.authSessionAbsoluteTimeoutSeconds ?? 0,
    authSessionSecureCookies:
      existing.authSessionSecureCookies ?? options.authSessionSecureCookies ?? '',
    authSessionRotateOnRefresh:
      existing.authSessionRotateOnRefresh ?? options.authSessionRotateOnRefresh ?? '',
  }

  const publicConfig: AdminUiPublicRuntimeConfig = {
    authEnabled: existing.public?.authEnabled ?? Boolean(config.authIssuer),
    appName: existing.public?.appName ?? options.appName ?? 'CBS Nova Admin',
    temporalUiBaseUrl: existing.public?.temporalUiBaseUrl ?? options.temporalUiBaseUrl ?? '',
    temporalNamespace: existing.public?.temporalNamespace ?? options.temporalNamespace ?? 'default',
  }

  return { config, publicConfig }
}
