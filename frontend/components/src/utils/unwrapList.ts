/**
 * Shared list-envelope unwrapping utilities.
 *
 * Backend list endpoints return either a bare array or a paged envelope
 * (`{ items: T[], total?: number }`). A few legacy endpoints historically
 * used `{ constructs: T[] }` as the array key.
 *
 * These helpers never throw on malformed inputs; they always return a plain
 * array (or an envelope wrapping one) so callers can consume them safely.
 */
export interface ListEnvelope<T> {
  items: T[]
  total?: number
}

function firstArrayFrom<T>(resp: unknown, keys: string[]): T[] | undefined {
  if (typeof resp !== 'object' || resp === null) return undefined
  for (const key of keys) {
    const candidate = (resp as Record<string, unknown>)[key]
    if (Array.isArray(candidate)) return candidate as T[]
  }
  return undefined
}

export function unwrapList<T>(resp: unknown): T[] {
  if (Array.isArray(resp)) return resp
  return firstArrayFrom<T>(resp, ['items', 'constructs']) ?? []
}

export function unwrapListWithTotal<T>(resp: unknown): ListEnvelope<T> {
  if (Array.isArray(resp)) {
    return { items: resp, total: resp.length }
  }

  const items = firstArrayFrom<T>(resp, ['items', 'constructs']) ?? []

  if (typeof resp !== 'object' || resp === null) {
    return { items, total: items.length }
  }

  const rawTotal = (resp as Record<string, unknown>).total
  const total = typeof rawTotal === 'number' && Number.isFinite(rawTotal) ? rawTotal : items.length

  return { items, total }
}
