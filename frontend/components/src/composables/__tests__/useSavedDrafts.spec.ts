import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { resetSavedDraftsState, type SavedDraftSummary, useSavedDrafts } from '../useSavedDrafts'

const DRAFTS: SavedDraftSummary[] = [
  { name: 'alpha', type: 'Process', status: 'Draft', updatedAt: 1 },
  { name: 'beta', type: 'Helper', status: 'Draft', updatedAt: 2 },
]

/**
 * Mounts a parent that consumes the composable with no options (like the layout)
 * and a child that registers the fetcher/handler (like the page). The parent's
 * setup runs FIRST — the regression this guards against is the parent seeing
 * nothing because the child had not registered yet.
 */
function mountParentChild(childOptions: Parameters<typeof useSavedDrafts>[0] = {}) {
  const parentSeen: ReturnType<typeof useSavedDrafts>[] = []

  const Child = defineComponent({
    setup() {
      useSavedDrafts(childOptions)
      return () => h('div', { class: 'child' })
    },
  })

  const Parent = defineComponent({
    setup() {
      const shared = useSavedDrafts()
      parentSeen.push(shared)
      return () => h('div', [h(Child)])
    },
  })

  const wrapper = mount(Parent)
  return { wrapper, parent: parentSeen[0] as ReturnType<typeof useSavedDrafts> }
}

describe('useSavedDrafts', () => {
  beforeEach(() => {
    resetSavedDraftsState()
  })

  it('starts empty', () => {
    const { parent } = mountParentChild()

    expect(parent.drafts.value).toEqual([])
    expect(parent.loading.value).toBe(false)
    expect(parent.error.value).toBeNull()
    expect(parent.selectedName.value).toBeNull()
  })

  it('lets a parent consumer refresh with a fetcher registered later by a child', async () => {
    const fetcher = vi.fn().mockResolvedValue(DRAFTS)
    const { parent } = mountParentChild({ fetcher })

    await parent.refresh()

    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(parent.drafts.value).toEqual(DRAFTS)
  })

  it('replays a refresh requested before any fetcher was registered', async () => {
    const fetcher = vi.fn().mockResolvedValue(DRAFTS)

    // Parent asks to refresh during its own setup, before the child registers.
    const Child = defineComponent({
      setup() {
        useSavedDrafts({ fetcher })
        return () => h('div')
      },
    })
    const Parent = defineComponent({
      setup() {
        const shared = useSavedDrafts()
        void shared.refresh()
        return () => h('div', [h(Child)])
      },
    })

    mount(Parent)
    await flushPromises()

    const { drafts } = useSavedDrafts()
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(drafts.value).toEqual(DRAFTS)
  })

  it('shares drafts loaded by the child with the parent reactively', async () => {
    const fetcher = vi.fn().mockResolvedValue(DRAFTS)
    const parentSeen: number[] = []

    const Child = defineComponent({
      setup() {
        const { refresh } = useSavedDrafts({ fetcher })
        void refresh()
        return () => h('div')
      },
    })
    const Parent = defineComponent({
      setup() {
        const { drafts } = useSavedDrafts()
        return () => {
          parentSeen.push(drafts.value.length)
          return h('div', [h('span', String(drafts.value.length)), h(Child)])
        }
      },
    })

    const wrapper = mount(Parent)
    await flushPromises()
    await nextTick()

    expect(wrapper.find('span').text()).toBe('2')
    expect(parentSeen.at(-1)).toBe(2)
  })

  it('records the error message and clears drafts when the fetcher rejects', async () => {
    const onError = vi.fn()
    const fetcher = vi.fn().mockRejectedValue(new Error('boom'))
    const { parent } = mountParentChild({ fetcher, onError })

    await parent.refresh()

    expect(parent.error.value).toBe('boom')
    expect(parent.drafts.value).toEqual([])
    expect(parent.loading.value).toBe(false)
    expect(onError).toHaveBeenCalledWith('boom')
  })

  it('coalesces concurrent refreshes into a single fetch', async () => {
    const fetcher = vi.fn().mockResolvedValue(DRAFTS)
    const { parent } = mountParentChild({ fetcher })

    await Promise.all([parent.refresh(), parent.refresh(), parent.refresh()])

    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('dispatches select to the handler registered by the child and reports it handled', () => {
    const onSelect = vi.fn()
    const { parent } = mountParentChild({ onSelect })

    expect(parent.select('alpha')).toBe(true)
    expect(onSelect).toHaveBeenCalledWith('alpha')
  })

  it('reports select as unhandled when no handler is registered', () => {
    const { parent } = mountParentChild()

    expect(parent.select('alpha')).toBe(false)
  })

  it('unregisters the select handler when the owning component unmounts', () => {
    const onSelect = vi.fn()
    const { wrapper, parent } = mountParentChild({ onSelect })

    wrapper.unmount()

    expect(parent.select('alpha')).toBe(false)
    expect(onSelect).not.toHaveBeenCalled()
  })

  it('shares selectedName between consumers', () => {
    const { parent } = mountParentChild()

    parent.selectedName.value = 'alpha'

    const { selectedName } = useSavedDrafts()
    expect(selectedName.value).toBe('alpha')
  })

  it('isolates state per Vue app so SSR requests do not leak into each other', async () => {
    const fetcherA = vi.fn().mockResolvedValue(DRAFTS)
    const fetcherB = vi.fn().mockResolvedValue([])

    const AppA = defineComponent({
      setup() {
        const { refresh, drafts } = useSavedDrafts({ fetcher: fetcherA })
        void refresh()
        return () => h('div', String(drafts.value.length))
      },
    })
    const AppB = defineComponent({
      setup() {
        const { drafts } = useSavedDrafts({ fetcher: fetcherB })
        return () => h('div', String(drafts.value.length))
      },
    })

    const a = mount(AppA)
    await flushPromises()
    const b = mount(AppB)
    await flushPromises()

    expect(a.text()).toBe('2')
    expect(b.text()).toBe('0')
    expect(fetcherB).not.toHaveBeenCalled()
  })
})
