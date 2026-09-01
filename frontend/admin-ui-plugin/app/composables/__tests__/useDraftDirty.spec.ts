import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, reactive, ref } from 'vue'

const innerState = reactive({ isDirty: false })
const mockWorkbench = {
  state: ref(innerState),
  markDirty: vi.fn(() => {
    innerState.isDirty = true
  }),
  markClean: vi.fn(() => {
    innerState.isDirty = false
  }),
}

vi.mock('@cbs/admin-ui-plugin/composables/useDslWorkbench', () => ({
  useDslWorkbench: vi.fn(() => mockWorkbench),
}))

import { useDraftDirty } from '../useDraftDirty'

describe('useDraftDirty', () => {
  beforeEach(() => {
    mockWorkbench.state.value.isDirty = false
    vi.clearAllMocks()
  })

  it('reflects the underlying workbench dirty state', () => {
    const { isDirty } = useDraftDirty()

    expect(isDirty.value).toBe(false)

    mockWorkbench.state.value.isDirty = true
    expect(isDirty.value).toBe(true)

    mockWorkbench.state.value.isDirty = false
    expect(isDirty.value).toBe(false)
  })

  it('delegates markDirty to the workbench', () => {
    const { isDirty, markDirty } = useDraftDirty()

    expect(isDirty.value).toBe(false)

    markDirty()

    expect(mockWorkbench.markDirty).toHaveBeenCalled()
    expect(isDirty.value).toBe(true)
  })

  it('delegates markClean to the workbench', () => {
    mockWorkbench.state.value.isDirty = true

    const { isDirty, markClean } = useDraftDirty()

    expect(isDirty.value).toBe(true)

    markClean()

    expect(mockWorkbench.markClean).toHaveBeenCalled()
    expect(isDirty.value).toBe(false)
  })

  it('does not create an independent dirty source', async () => {
    const { isDirty } = useDraftDirty()

    // Mutating the underlying state should immediately be visible through the
    // wrapper because `isDirty` is derived from the workbench state.
    mockWorkbench.state.value.isDirty = true
    await nextTick()

    expect(isDirty.value).toBe(true)
  })
})
