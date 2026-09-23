import { describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { useHelperSearch } from '../useHelperSearch'

describe('useHelperSearch', () => {
  it('returns empty results and not loading by default', () => {
    const { results, isLoading, error } = useHelperSearch({
      fetch: vi.fn().mockResolvedValue([]),
    })

    expect(results.value).toEqual([])
    expect(isLoading.value).toBe(false)
    expect(error.value).toBeNull()
  })

  it('executes a search and stores results', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValue([
        { name: 'H1', type: 'helper', description: 'd', inputType: 'String', outputType: 'Number' },
      ])

    const { filters, execute, results, isLoading } = useHelperSearch({ fetch, debounceMs: 0 })

    filters.value.query = 'H1'
    await execute()

    expect(isLoading.value).toBe(false)
    expect(fetch).toHaveBeenCalledWith({ query: 'H1', mode: 'exact' })
    expect(results.value).toHaveLength(1)
    expect(results.value[0].name).toBe('H1')
  })

  it('stores an error message when fetch fails', async () => {
    const fetch = vi.fn().mockRejectedValue(new Error('network down'))

    const { execute, error, results } = useHelperSearch({ fetch, debounceMs: 0 })

    await execute()

    expect(error.value).toBe('network down')
    expect(results.value).toEqual([])
  })

  it('uses a generic error message when the rejection is not an Error', async () => {
    const fetch = vi.fn().mockRejectedValue('bad')

    const { execute, error } = useHelperSearch({ fetch, debounceMs: 0 })

    await execute()

    expect(error.value).toBe('Failed to search objects')
  })

  it('debounces search calls', async () => {
    vi.useFakeTimers()
    const fetch = vi.fn().mockResolvedValue([])

    const { filters, search, isLoading } = useHelperSearch({ fetch, debounceMs: 200 })

    filters.value.query = 'a'
    search()
    filters.value.query = 'ab'
    search()
    filters.value.query = 'abc'
    search()

    expect(isLoading.value).toBe(false)
    expect(fetch).not.toHaveBeenCalled()

    vi.advanceTimersByTime(200)
    await nextTick()

    expect(fetch).toHaveBeenCalledTimes(1)
    expect(fetch).toHaveBeenCalledWith({ query: 'abc', mode: 'exact' })

    vi.useRealTimers()
  })

  it('computes hasActiveFilters correctly', () => {
    const { filters, hasActiveFilters } = useHelperSearch({
      fetch: vi.fn().mockResolvedValue([]),
    })

    expect(hasActiveFilters.value).toBe(false)

    filters.value.mode = 'fuzzy'
    expect(hasActiveFilters.value).toBe(true)

    filters.value.mode = 'exact'
    filters.value.query = 'helper'
    expect(hasActiveFilters.value).toBe(true)

    filters.value.query = ' '
    expect(hasActiveFilters.value).toBe(false)
  })

  it('clearFilters resets filters and re-runs the search', async () => {
    const fetch = vi.fn().mockResolvedValue([])

    const { filters, clearFilters, results, error } = useHelperSearch({
      fetch,
      debounceMs: 0,
    })

    filters.value = { query: 'x', mode: 'fuzzy' }
    await clearFilters()

    expect(filters.value).toEqual({ query: '', mode: 'exact' })
    expect(results.value).toEqual([])
    expect(error.value).toBeNull()
    expect(fetch).toHaveBeenCalledWith({ query: '', mode: 'exact' })
  })
})

describe('useHelperSearch initialFilters', () => {
  it('seeds filters from initialFilters when provided', () => {
    const { filters, hasActiveFilters } = useHelperSearch({
      fetch: vi.fn().mockResolvedValue([]),
      initialFilters: { query: 'Foo', mode: 'fuzzy' },
    })

    expect(filters.value).toEqual({ query: 'Foo', mode: 'fuzzy' })
    expect(hasActiveFilters.value).toBe(true)
  })

  it('falls back to empty defaults when initialFilters is omitted', () => {
    const { filters } = useHelperSearch({
      fetch: vi.fn().mockResolvedValue([]),
    })

    expect(filters.value).toEqual({ query: '', mode: 'exact' })
  })

  it('tolerates a partial initialFilters object', () => {
    const { filters } = useHelperSearch({
      fetch: vi.fn().mockResolvedValue([]),
      initialFilters: { mode: 'fuzzy' },
    })

    expect(filters.value).toEqual({ query: '', mode: 'fuzzy' })
  })
})
