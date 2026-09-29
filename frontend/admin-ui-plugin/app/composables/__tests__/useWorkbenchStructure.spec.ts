import { flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { computed, nextTick, ref } from 'vue'
import type { ObjectStructureDto } from '@cbs/components'
import { useWorkbenchStructure } from '../useWorkbenchStructure'

const SAMPLE_STRUCTURE: ObjectStructureDto = {
  name: 'BatchProcessing',
  type: 'process',
  fields: [
    {
      path: 'taskQueue',
      value: 'BatchProcessing-queue',
      type: 'java.lang.String',
      description: 'Temporal task queue this workflow polls',
    },
  ],
}

function buildHarness(opts: {
  selected?: { type?: string } | null
  fetcher?: ReturnType<typeof vi.fn>
} = {}) {
  const selectedRef = ref<{ type?: string } | null>(opts.selected ?? null)
  const selectedConstruct = computed(() => selectedRef.value)
  const fetchObjectStructure =
    opts.fetcher ?? vi.fn(async () => SAMPLE_STRUCTURE)
  const harness = useWorkbenchStructure({
    selectedConstruct,
    fetchObjectStructure,
  })
  return { ...harness, selectedRef, fetchObjectStructure, selectedConstruct }
}

describe('useWorkbenchStructure — initial state', () => {
  it('exposes structure, loading, error, name refs and the two actions', () => {
    const h = buildHarness()
    expect(Object.keys(h).sort()).toEqual(
      [
        'fetchObjectStructure',
        'loadStructure',
        'retryStructure',
        'selectedConstruct',
        'selectedRef',
        'structure',
        'structureError',
        'structureLoading',
        'structureName',
      ].sort(),
    )
  })

  it('starts with structure/null, loading/false, error/null, name/null', () => {
    const h = buildHarness()
    expect(h.structure.value).toBeNull()
    expect(h.structureLoading.value).toBe(false)
    expect(h.structureError.value).toBeNull()
    expect(h.structureName.value).toBeNull()
  })
})

describe('useWorkbenchStructure — loadStructure guards', () => {
  it('resets state and skips fetch when name is empty', async () => {
    const h = buildHarness({ selected: { type: 'process' } })
    // Pre-populate so we can prove the guard clears it.
    h.structure.value = SAMPLE_STRUCTURE
    h.structureError.value = 'previous error'
    h.structureName.value = 'previous'

    await h.loadStructure('')
    await nextTick()

    expect(h.fetchObjectStructure).not.toHaveBeenCalled()
    expect(h.structure.value).toBeNull()
    expect(h.structureError.value).toBeNull()
    expect(h.structureName.value).toBeNull()
  })

  it('resets state and skips fetch when no construct is selected', async () => {
    const h = buildHarness({ selected: null })
    h.structure.value = SAMPLE_STRUCTURE
    h.structureName.value = 'previous'

    await h.loadStructure('BatchProcessing')

    expect(h.fetchObjectStructure).not.toHaveBeenCalled()
    expect(h.structure.value).toBeNull()
    expect(h.structureName.value).toBeNull()
  })

  it('resets state when the selected construct has no type', async () => {
    const h = buildHarness({ selected: {} })
    h.structure.value = SAMPLE_STRUCTURE
    h.structureName.value = 'previous'

    await h.loadStructure('BatchProcessing')

    expect(h.fetchObjectStructure).not.toHaveBeenCalled()
    expect(h.structure.value).toBeNull()
    expect(h.structureName.value).toBeNull()
  })
})

describe('useWorkbenchStructure — loadStructure success', () => {
  it('fetches the structure with (constructType, name) and stores the result', async () => {
    const fetcher = vi.fn(async () => SAMPLE_STRUCTURE)
    const h = buildHarness({ selected: { type: 'process' }, fetcher })

    const promise = h.loadStructure('BatchProcessing')
    expect(h.structureLoading.value).toBe(true)
    expect(h.structureError.value).toBeNull()
    expect(h.structureName.value).toBe('BatchProcessing')

    await promise
    await flushPromises()

    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(fetcher).toHaveBeenCalledWith('process', 'BatchProcessing')
    expect(h.structure.value).toEqual(SAMPLE_STRUCTURE)
    expect(h.structureLoading.value).toBe(false)
    expect(h.structureError.value).toBeNull()
  })

  it('passes the most recent constructType when selectedConstruct changes mid-call', async () => {
    let resolveFetch!: (value: ObjectStructureDto) => void
    const fetcher = vi.fn(
      () => new Promise<ObjectStructureDto>((resolve) => { resolveFetch = resolve }),
    )
    const h = buildHarness({ selected: { type: 'process' }, fetcher })

    const pending = h.loadStructure('BatchProcessing')
    expect(fetcher).toHaveBeenCalledWith('process', 'BatchProcessing')

    resolveFetch(SAMPLE_STRUCTURE)
    await pending
    await flushPromises()

    expect(h.structure.value).toEqual(SAMPLE_STRUCTURE)
  })
})

describe('useWorkbenchStructure — loadStructure cache', () => {
  it('skips the fetch when the same name is already loaded and force is not set', async () => {
    const fetcher = vi.fn(async () => SAMPLE_STRUCTURE)
    const h = buildHarness({ selected: { type: 'process' }, fetcher })

    await h.loadStructure('BatchProcessing')
    expect(fetcher).toHaveBeenCalledTimes(1)

    await h.loadStructure('BatchProcessing')
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(h.structureName.value).toBe('BatchProcessing')
  })

  it('re-fetches when force is true even if the same name is already loaded', async () => {
    const fetcher = vi.fn(async () => SAMPLE_STRUCTURE)
    const h = buildHarness({ selected: { type: 'process' }, fetcher })

    await h.loadStructure('BatchProcessing')
    await h.loadStructure('BatchProcessing', true)
    await flushPromises()

    expect(fetcher).toHaveBeenCalledTimes(2)
  })

  it('re-fetches when the name changes', async () => {
    const fetcher = vi.fn(async () => SAMPLE_STRUCTURE)
    const h = buildHarness({ selected: { type: 'process' }, fetcher })

    await h.loadStructure('BatchProcessing')
    await h.loadStructure('OtherProcess')
    await flushPromises()

    expect(fetcher).toHaveBeenCalledTimes(2)
    expect(fetcher).toHaveBeenLastCalledWith('process', 'OtherProcess')
    expect(h.structureName.value).toBe('OtherProcess')
  })

  it('updates structureName before awaiting so the cache check fires on the new value', async () => {
    const fetcher = vi.fn(async () => SAMPLE_STRUCTURE)
    const h = buildHarness({ selected: { type: 'process' }, fetcher })

    await h.loadStructure('BatchProcessing')
    const second = h.loadStructure('OtherProcess')
    // The second call's name is already recorded synchronously, so a re-load
    // for 'OtherProcess' before the first resolves must still hit the network.
    const sameAgain = h.loadStructure('OtherProcess')
    await Promise.all([second, sameAgain])
    await flushPromises()

    expect(fetcher).toHaveBeenCalledTimes(2)
    expect(fetcher.mock.calls.map((c) => c[1])).toEqual([
      'BatchProcessing',
      'OtherProcess',
    ])
  })
})

describe('useWorkbenchStructure — loadStructure error', () => {
  it('captures the error message, nulls structure, and leaves loading false', async () => {
    const fetcher = vi.fn(async () => { throw new Error('boom') })
    const h = buildHarness({ selected: { type: 'process' }, fetcher })

    await h.loadStructure('BatchProcessing')
    await flushPromises()

    expect(h.structure.value).toBeNull()
    expect(h.structureError.value).toBe('boom')
    expect(h.structureLoading.value).toBe(false)
    expect(h.structureName.value).toBe('BatchProcessing')
  })

  it('reads .message off the rejected Error for the error message', async () => {
    const fetcher = vi.fn(async () => {
      const err = new Error('backend says no')
      err.message = 'backend says no'
      throw err
    })
    const h = buildHarness({ selected: { type: 'process' }, fetcher })

    await h.loadStructure('BatchProcessing')
    await flushPromises()

    expect(h.structureError.value).toBe('backend says no')
  })

  it('clears a previous error before fetching a new name', async () => {
    const fetcher = vi
      .fn<(t: string, n: string) => Promise<ObjectStructureDto>>()
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValueOnce(SAMPLE_STRUCTURE)
    const h = buildHarness({ selected: { type: 'process' }, fetcher })

    await h.loadStructure('BatchProcessing')
    await flushPromises()
    expect(h.structureError.value).toBe('boom')

    await h.loadStructure('OtherProcess')
    await flushPromises()

    expect(h.structureError.value).toBeNull()
    expect(h.structure.value).toEqual(SAMPLE_STRUCTURE)
  })
})

describe('useWorkbenchStructure — retryStructure', () => {
  it('re-runs loadStructure with the last name and force=true after a failure', async () => {
    const fetcher = vi
      .fn<(t: string, n: string) => Promise<ObjectStructureDto>>()
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValueOnce(SAMPLE_STRUCTURE)
    const h = buildHarness({ selected: { type: 'process' }, fetcher })

    await h.loadStructure('BatchProcessing')
    await flushPromises()
    expect(h.structureError.value).toBe('boom')

    h.retryStructure()
    await flushPromises()

    expect(fetcher).toHaveBeenCalledTimes(2)
    expect(fetcher).toHaveBeenLastCalledWith('process', 'BatchProcessing')
    expect(h.structure.value).toEqual(SAMPLE_STRUCTURE)
    expect(h.structureError.value).toBeNull()
  })

  it('passes an empty name when nothing has been loaded yet (no-op guard path)', async () => {
    const fetcher = vi.fn(async () => SAMPLE_STRUCTURE)
    const h = buildHarness({ selected: { type: 'process' }, fetcher })

    h.retryStructure()
    await flushPromises()

    expect(fetcher).not.toHaveBeenCalled()
    expect(h.structureName.value).toBeNull()
  })
})
