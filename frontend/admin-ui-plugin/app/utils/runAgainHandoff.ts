// T293 — "Run again" handoff between the execution detail page and the runner.
//
// The execution detail page stashes the process name + input it wants to
// re-run, then navigates to /runner with name/mode in the query string. The
// runner consumes the stash after it selects the matching definition so the
// input form is pre-filled. The handoff is intentionally one-shot: consuming
// always removes the key, whether or not the name matches (a mismatch is
// treated as a stale stash and discarded), so a later unrelated visit to the
// runner is never accidentally pre-filled.
const STORAGE_KEY = 'cbs.nova.run-again'

/**
 * Persist a run-again handoff for a given process name.
 *
 * SSR-safe (no-op when `window` is unavailable) and swallows storage
 * failures (private mode, quota, etc.) so navigation is never blocked.
 */
export function stashRunAgain(name: string, input: unknown): void {
  if (typeof window === 'undefined') return
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ name, input }))
  } catch {
    // ignore storage failures — worst case the runner isn't pre-filled
  }
}

/**
 * Read and consume a run-again handoff that targets `expectedName`.
 *
 * Returns the stashed input as-is, or `null` when there is nothing stashed,
 * the JSON is malformed, or the stashed name does not match `expectedName`.
 * The key is always removed (best-effort), so a match is never re-consumed
 * and a stale/malformed stash can't poison a later visit.
 */
export function consumeRunAgain(expectedName: string): unknown | null {
  if (typeof window === 'undefined') return null

  let raw: string | null = null
  try {
    raw = window.sessionStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
  if (raw === null) return null

  let parsed: { name?: unknown; input?: unknown }
  try {
    parsed = JSON.parse(raw) as { name?: unknown; input?: unknown }
  } catch {
    removeStash()
    return null
  }

  // Always discard the stash, whether or not it matches.
  removeStash()

  if (parsed.name !== expectedName) return null
  return parsed.input ?? null
}

function removeStash(): void {
  try {
    window.sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // ignore storage failures
  }
}