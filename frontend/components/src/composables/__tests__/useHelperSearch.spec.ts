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

    filters.value.name = 'H1'
    await execute()

    expect(isLoading.value).toBe(false)
    expect(fetch).toHaveBeenCalledWith({ name: 'H1', type: '', description: '' })
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

    expect(error.value).toBe('Failed to search helpers')
  })

  it('debounces search calls', async () => {
    vi.useFakeTimers()
    const fetch = vi.fn().mockResolvedValue([])

    const { filters, search, isLoading } = useHelperSearch({ fetch, debounceMs: 200 })

    filters.value.name = 'a'
    search()
    filters.value.name = 'ab'
    search()
    filters.value.name = 'abc'
    search()

    expect(isLoading.value).toBe(false)
    expect(fetch).not.toHaveBeenCalled()

    vi.advanceTimersByTime(200)
    await nextTick()

    expect(fetch).toHaveBeenCalledTimes(1)
    expect(fetch).toHaveBeenCalledWith({ name: 'abc', type: '', description: '' })

    vi.useRealTimers()
  })

  it('computes hasActiveFilters correctly', () => {
    const { filters, hasActiveFilters } = useHelperSearch({
      fetch: vi.fn().mockResolvedValue([]),
    })

    expect(hasActiveFilters.value).toBe(false)

    filters.value.type = 'helper'
    expect(hasActiveFilters.value).toBe(true)

    filters.value.type = ' '
    expect(hasActiveFilters.value).toBe(false)
  })

  it('clearFilters resets filters and re-runs the search', async () => {
    const fetch = vi.fn().mockResolvedValue([])

    const { filters, clearFilters, results, error } = useHelperSearch({
      fetch,
      debounceMs: 0,
    })

    filters.value = { name: 'x', type: 'y', description: 'z' }
    await clearFilters()

    expect(filters.value).toEqual({ name: '', type: '', description: '' })
    expect(results.value).toEqual([])
    expect(error.value).toBeNull()
    expect(fetch).toHaveBeenCalledWith({ name: '', type: '', description: '' })
  })
})
