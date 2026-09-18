import { computed, type Ref } from 'vue'

type ManifestGuardVerdict = {
  id: string
  allowed: boolean
  reason?: string
}

type ManifestGuardState = {
  ready: boolean
  /**
   * False when the fetch failed or the payload was malformed. Fail-closed marker: while
   * `ok` is false every piece is denied, regardless of `verdicts`.
   */
  ok: boolean
  verdicts: Record<string, boolean>
}

/**
 * T551 manifest-guard consumer — defense-in-depth UX sugar ONLY.
 *
 * The security boundary is T549's server-side `PieceGuardFilter` enforcement: the backend
 * re-evaluates every guard at execution time, so a denied verdict rendered here is a hint,
 * not a gate, and an allowed verdict never grants anything by itself. NEVER rely on
 * client-side hiding/disabling alone — this composable only renders verdicts the backend
 * resolved for the current principal (`GET /api/v1/dsl/manifest/guard`, which leaks no
 * check internals: no role names, no flag names — verdicts only).
 *
 * Semantics:
 * - Fetches the verdict snapshot once per session (`callOnce` + `useState`, same pattern as
 *   `useAuth`).
 * - FAILS CLOSED: fetch failure, 401/403, or a malformed payload denies EVERY piece; while
 *   `ready` is false every piece is denied too, so bound buttons must use
 *   `:disabled="!allowed('piece-id')"` and never flash an enabled state before the verdict
 *   arrives (hide, don't show).
 * - A piece id absent from the manifest snapshot is not governed by the manifest and reads
 *   as allowed once `ready` — the server-side guard remains authoritative at click time.
 */
export function useManifestGuard() {
  const state = useState<ManifestGuardState>('cbs-manifest-guard', () => ({
    ready: false,
    ok: false,
    verdicts: {},
  }))

  async function load(): Promise<void> {
    try {
      const data = await $fetch<unknown>('/api/v1/dsl/manifest/guard')
      if (!Array.isArray(data)) {
        throw new Error('malformed manifest guard payload')
      }
      const verdicts: Record<string, boolean> = {}
      for (const item of data) {
        const verdict = item as ManifestGuardVerdict | null
        if (
          verdict === null ||
          typeof verdict !== 'object' ||
          typeof verdict.id !== 'string' ||
          typeof verdict.allowed !== 'boolean'
        ) {
          throw new Error('malformed manifest guard payload')
        }
        verdicts[verdict.id] = verdict.allowed
      }
      state.value = { ready: true, ok: true, verdicts }
    } catch {
      // Fail closed: network error, 401/403, or malformed payload → deny every piece.
      state.value = { ready: true, ok: false, verdicts: {} }
    }
  }

  // Kick off the once-per-session fetch (SSR and client share the callOnce key).
  callOnce('cbs-manifest-guard', load)

  const ready: Ref<boolean> = computed(() => state.value.ready)

  function allowed(pieceId: string): boolean {
    const snapshot = state.value
    if (!snapshot.ready || !snapshot.ok) {
      return false
    }
    return snapshot.verdicts[pieceId] !== false
  }

  return { allowed, ready }
}
