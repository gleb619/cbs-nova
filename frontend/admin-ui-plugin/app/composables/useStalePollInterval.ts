import { useRuntimeConfig } from 'nuxt/app'

export const DEFAULT_STALE_POLL_MS = 5000

/**
 * Resolves the stale-poll interval from an explicit override, then from
 * runtimeConfig.public.stalePollMs, falling back to 5000 ms.
 *
 * Tolerates non-Nuxt contexts: if useRuntimeConfig throws, the fallback
 * is returned without propagating the error.
 */
export function resolveStalePollMs(explicit?: number): number {
  if (typeof explicit === 'number' && explicit > 0) return explicit
  try {
    const fromConfig = useRuntimeConfig().public?.stalePollMs
    if (typeof fromConfig === 'number' && fromConfig > 0) return fromConfig
  } catch {
    // not in a Nuxt context — fall through to default
  }
  return DEFAULT_STALE_POLL_MS
}
