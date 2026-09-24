import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { useWorkbenchObjectSearch } from '../useWorkbenchObjectSearch'

const STORAGE_KEY = 'cbs-nova:dsl-workbench:object-search-filters'

function createSearchObjects() {
  return vi.fn().mockResolvedValue({
    items: [{ name: 'N1', type: 'process', description: '', inputType: '', outputType: '' }],
  })
}

function createComposable(searchObjects = createSearchObjects()) {
  return {
    searchObjects,
    ...useWorkbenchObjectSearch({ searchObjects, listHelpers: vi.fn().mockResolvedValue({}) }),
  }
}

describe('useWorkbenchObjectSearch', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('defaults filters to an empty exact search across all types', () => {
    const { filters } = createComposable()

    expect(filters.value).toEqual({ query: '', mode: 'exact', type: '' })
  })

  it('restores saved filters from localStorage', () => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ query: 'Pay', mode: 'fuzzy', type: 'process' }),
    )

    const { filters } = createComposable()

    expect(filters.value).toEqual({ query: 'Pay', mode: 'fuzzy', type: 'process' })
  })

  it('persists filter changes to localStorage', async () => {
    const { filters } = createComposable()

    filters.value.query = 'Pay'
    filters.value.type = 'helper'
    await nextTick()

    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toEqual({
      query: 'Pay',
      mode: 'exact',
      type: 'helper',
    })
  })

  it('exposes hasActiveFilters from the underlying helper search', async () => {
    const searchObjects = createSearchObjects()
    const { filters, hasActiveFilters } = useWorkbenchObjectSearch({
      searchObjects,
      listHelpers: vi.fn().mockResolvedValue({}),
    })

    expect(hasActiveFilters.value).toBe(false)

    filters.value.query = 'Order'
    await nextTick()

    expect(hasActiveFilters.value).toBe(true)
  })

  it('passes the type filter to the backend, omitting it for All types', async () => {
    vi.useFakeTimers()
    const searchObjects = createSearchObjects()
    const { filters, search } = useWorkbenchObjectSearch({
      searchObjects,
      listHelpers: vi.fn().mockResolvedValue({}),
    })

    filters.value.type = 'process'
    search()
    await vi.advanceTimersByTimeAsync(250)

    expect(searchObjects).toHaveBeenLastCalledWith(expect.objectContaining({ type: 'process' }))

    filters.value.type = ''
    search()
    await vi.advanceTimersByTimeAsync(250)

    expect(searchObjects).toHaveBeenLastCalledWith(expect.objectContaining({ type: undefined }))
    vi.useRealTimers()
  })

  it('clearSaved resets persisted filters and clears results', async () => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ query: 'Pay', mode: 'fuzzy', type: 'process' }),
    )
    const searchObjects = createSearchObjects()
    const { clearSaved } = useWorkbenchObjectSearch({
      searchObjects,
      listHelpers: vi.fn().mockResolvedValue({}),
    })

    await clearSaved()

    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toEqual({
      query: '',
      mode: 'exact',
      type: '',
    })
    expect(searchObjects).toHaveBeenCalledWith(
      expect.objectContaining({ query: '', mode: 'exact', type: undefined }),
    )
  })

  it('save persists the current filters and runs the search immediately', async () => {
    vi.useFakeTimers()
    const searchObjects = createSearchObjects()
    const { filters, save } = useWorkbenchObjectSearch({
      searchObjects,
      listHelpers: vi.fn().mockResolvedValue({}),
    })

    filters.value = { query: 'Order', mode: 'cosine', type: 'transaction' }
    await save()

    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toEqual({
      query: 'Order',
      mode: 'cosine',
      type: 'transaction',
    })
    expect(searchObjects).toHaveBeenCalledWith(
      expect.objectContaining({ query: 'Order', mode: 'cosine', type: 'transaction' }),
    )
    vi.useRealTimers()
  })
})
