import { describe, expect, it } from 'vitest'
import { unwrapList, unwrapListWithTotal } from '../unwrapList'

describe('unwrapList', () => {
  it('returns a bare array as-is', () => {
    const arr = [{ id: 1 }, { id: 2 }]
    expect(unwrapList(arr)).toBe(arr)
  })

  it('unwraps { items: [...] }', () => {
    const items = [{ id: 1 }, { id: 2 }]
    expect(unwrapList({ items })).toEqual(items)
  })

  it('unwraps the legacy { constructs: [...] } alias', () => {
    const constructs = [{ name: 'a' }, { name: 'b' }]
    expect(unwrapList({ constructs })).toEqual(constructs)
  })

  it('prefers .items over .constructs when both are present', () => {
    const items = [{ name: 'from-items' }]
    expect(unwrapList({ items, constructs: [{ name: 'from-constructs' }] })).toEqual(items)
  })

  it('returns [] for an empty object', () => {
    expect(unwrapList({})).toEqual([])
  })

  it('returns [] for null', () => {
    expect(unwrapList(null)).toEqual([])
  })

  it('returns [] for undefined', () => {
    expect(unwrapList(undefined)).toEqual([])
  })

  it('returns [] for non-object, non-array values', () => {
    expect(unwrapList('not a list')).toEqual([])
    expect(unwrapList(42)).toEqual([])
  })

  it('returns [] when the candidate keys are not arrays', () => {
    expect(unwrapList({ items: 42 })).toEqual([])
    expect(unwrapList({ constructs: 'nope' })).toEqual([])
  })
})

describe('unwrapListWithTotal', () => {
  it('returns a bare array with total equal to its length', () => {
    const arr = [{ id: 1 }, { id: 2 }]
    expect(unwrapListWithTotal(arr)).toEqual({ items: arr, total: 2 })
  })

  it('carries the provided total for an envelope', () => {
    const items = [{ id: 1 }]
    expect(unwrapListWithTotal({ items, total: 42 })).toEqual({ items, total: 42 })
  })

  it('falls back to items.length when total is missing', () => {
    const items = [{ id: 1 }, { id: 2 }]
    expect(unwrapListWithTotal({ items }).total).toBe(2)
  })

  it('falls back to items.length when total is NaN', () => {
    const items = [{ id: 1 }, { id: 2 }]
    expect(unwrapListWithTotal({ items, total: NaN }).total).toBe(2)
  })

  it('falls back to items.length for non-finite totals', () => {
    const items = [{ id: 1 }]
    expect(unwrapListWithTotal({ items, total: Infinity }).total).toBe(1)
    expect(unwrapListWithTotal({ items, total: -Infinity }).total).toBe(1)
  })

  it('ignores non-numeric totals', () => {
    const items = [{ id: 1 }]
    expect(unwrapListWithTotal({ items, total: '10' as unknown as number }).total).toBe(1)
  })

  it('unwraps the legacy { constructs: [...] } alias', () => {
    const constructs = [{ name: 'a' }, { name: 'b' }]
    expect(unwrapListWithTotal({ constructs })).toEqual({ items: constructs, total: 2 })
  })

  it('returns an empty envelope for an empty object', () => {
    expect(unwrapListWithTotal({})).toEqual({ items: [], total: 0 })
  })

  it('returns an empty envelope for null / undefined / strings', () => {
    expect(unwrapListWithTotal(null)).toEqual({ items: [], total: 0 })
    expect(unwrapListWithTotal(undefined)).toEqual({ items: [], total: 0 })
    expect(unwrapListWithTotal('nope')).toEqual({ items: [], total: 0 })
  })
})
