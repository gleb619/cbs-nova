import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { resetSavedDraftsState, type SavedDraftSummary } from '@cbs/components'
import { useWorkbenchSavedDrafts } from '../useWorkbenchSavedDrafts'

const ONE_DRAFT: SavedDraftSummary[] = [
  { name: 'alpha', type: 'Process', status: 'Draft', updatedAt: 1 },
]

const MANY_DRAFTS: SavedDraftSummary[] = [
  { name: 'alpha', type: 'Process', status: 'Draft', updatedAt: 1 },
  { name: 'beta', type: 'Helper', status: 'Draft', updatedAt: 2 },
  { name: 'gamma', type: 'Function', status: 'Review', updatedAt: 3 },
]

interface Harness {
  drafts: ReturnType<typeof useWorkbenchSavedDrafts>['drafts']
  loading: ReturnType<typeof useWorkbenchSavedDrafts>['loading']
  error: ReturnType<typeof useWorkbenchSavedDrafts>['error']
  selectedName: ReturnType<typeof useWorkbenchSavedDrafts>['selectedName']
  refresh: ReturnType<typeof useWorkbenchSavedDrafts>['refresh']
  select: ReturnType<typeof useWorkbenchSavedDrafts>['select']
}

function mountHarness(opts: {
  fetcher?: () => Promise<unknown>
  logError?: ReturnType<typeof vi.fn>
  selectConstruct?: ReturnType<typeof vi.fn>
} = {}): { harness: Harness; logError: ReturnType<typeof vi.fn>; selectConstruct: ReturnType<typeof vi.fn>; unmount: () => void } {
  const logError = opts.logError ?? vi.fn()
  const selectConstruct = opts.selectConstruct ?? vi.fn()
  let harnessRef!: Harness

  const Wrapper = defineComponent({
    setup() {
      harnessRef = useWorkbenchSavedDrafts({
        listDrafts: opts.fetcher ?? (async () => []),
        logError,
        selectConstruct,
      })
      return () => h('div')
    },
  })

  const wrapper = mount(Wrapper)
  return {
    harness: harnessRef,
    logError,
    selectConstruct,
    unmount: () => wrapper.unmount(),
  }
}

describe('useWorkbenchSavedDrafts', () => {
  beforeEach(() => {
    resetSavedDraftsState()
  })

  it('exposes the same shape as useSavedDrafts', () => {
    const { harness } = mountHarness()
    expect(Object.keys(harness).sort()).toEqual(
      ['drafts', 'error', 'loading', 'refresh', 'select', 'selectedName'].sort(),
    )
  })

  it('starts with zero drafts, loading false, no error, no selected name', () => {
    const { harness } = mountHarness()
    expect(harness.drafts.value).toEqual([])
    expect(harness.loading.value).toBe(false)
    expect(harness.error.value).toBeNull()
    expect(harness.selectedName.value).toBeNull()
  })

  it('derives the list of one draft from listDrafts()', async () => {
    const fetcher = vi.fn(async () => ONE_DRAFT)
    const { harness } = mountHarness({ fetcher })

    await harness.refresh()
    await flushPromises()

    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(harness.drafts.value).toEqual(ONE_DRAFT)
    expect(harness.loading.value).toBe(false)
    expect(harness.error.value).toBeNull()
  })

  it('derives the list of many drafts from listDrafts()', async () => {
    const fetcher = vi.fn(async () => MANY_DRAFTS)
    const { harness } = mountHarness({ fetcher })

    await harness.refresh()
    await flushPromises()

    expect(harness.drafts.value).toEqual(MANY_DRAFTS)
    expect(harness.drafts.value).toHaveLength(3)
  })

  it('toggles loading while a refresh is in flight', async () => {
    let resolveFetch!: (value: SavedDraftSummary[]) => void
    const fetcher = vi.fn(
      () => new Promise<SavedDraftSummary[]>((resolve) => { resolveFetch = resolve }),
    )
    const { harness } = mountHarness({ fetcher })

    const pending = harness.refresh()
    expect(harness.loading.value).toBe(true)

    resolveFetch(MANY_DRAFTS)
    await pending
    await nextTick()

    expect(harness.loading.value).toBe(false)
  })

  it('forwards listDrafts() results without re-shaping (no envelope unwrapping here)', async () => {
    // useSavedDrafts unwraps { items: [...] }; we pin that the adapter passes
    // the raw value through unchanged — the unwrap happens in the underlying
    // composable, not in the workbench adapter.
    const envelope = { items: MANY_DRAFTS, total: 3 }
    const fetcher = vi.fn(async () => envelope)
    const { harness } = mountHarness({ fetcher })

    await harness.refresh()
    await flushPromises()

    expect(fetcher).toHaveBeenCalledTimes(1)
    // useSavedDrafts unwraps the envelope, so drafts reflects the items.
    expect(harness.drafts.value).toEqual(MANY_DRAFTS)
  })

  it('reports failures via logError with the "failed to load drafts" label', async () => {
    const fetcher = vi.fn(async () => { throw new Error('boom') })
    const logError = vi.fn()
    const { harness } = mountHarness({ fetcher, logError })

    await harness.refresh()
    await flushPromises()

    expect(harness.error.value).toBe('boom')
    expect(harness.drafts.value).toEqual([])
    expect(logError).toHaveBeenCalledTimes(1)
    expect(logError).toHaveBeenCalledWith('failed to load drafts', { error: 'boom' })
  })

  it('coerces non-Error rejections to a string for both error state and log fields', async () => {
    const fetcher = vi.fn(async () => { throw 'plain' })
    const logError = vi.fn()
    const { harness } = mountHarness({ fetcher, logError })

    await harness.refresh()
    await flushPromises()

    expect(harness.error.value).toBe('plain')
    expect(logError).toHaveBeenCalledWith('failed to load drafts', { error: 'plain' })
  })

  it('dispatches select() picks to options.selectConstruct', async () => {
    const fetcher = vi.fn(async () => MANY_DRAFTS)
    const selectConstruct = vi.fn()
    const { harness } = mountHarness({ fetcher, selectConstruct })

    await harness.refresh()
    await flushPromises()

    expect(harness.select('beta')).toBe(true)
    expect(selectConstruct).toHaveBeenCalledWith('beta')
    expect(selectConstruct).toHaveBeenCalledTimes(1)
  })

  it('reports select as unhandled when the adapter has been unmounted', async () => {
    const fetcher = vi.fn(async () => MANY_DRAFTS)
    const selectConstruct = vi.fn()
    const { harness, unmount } = mountHarness({ fetcher, selectConstruct })

    await harness.refresh()
    await flushPromises()

    unmount()

    expect(harness.select('beta')).toBe(false)
    expect(selectConstruct).not.toHaveBeenCalled()
  })

  it('keeps a separate store per mount so concurrent pages do not share state', async () => {
    const fetcherA = vi.fn(async () => ONE_DRAFT)
    const fetcherB = vi.fn(async () => MANY_DRAFTS)
    const a = mountHarness({ fetcher: fetcherA })
    const b = mountHarness({ fetcher: fetcherB })

    await Promise.all([a.harness.refresh(), b.harness.refresh()])
    await flushPromises()

    expect(a.harness.drafts.value).toEqual(ONE_DRAFT)
    expect(b.harness.drafts.value).toEqual(MANY_DRAFTS)
  })
})
