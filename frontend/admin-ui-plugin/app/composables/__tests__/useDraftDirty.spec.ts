import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive, ref } from 'vue'
import { createEmitter } from '../../utils/createEmitter'

const innerState = reactive({ isDirty: false })
const dirtyEmitter = createEmitter<{ dirty: undefined; clean: undefined }>()
const mockWorkbench = {
  state: ref(innerState),
  markDirty: vi.fn(() => {
    innerState.isDirty = true
    dirtyEmitter.emit('dirty')
  }),
  markClean: vi.fn(() => {
    innerState.isDirty = false
    dirtyEmitter.emit('clean')
  }),
  onDirty: (handler: () => void) => dirtyEmitter.on('dirty', handler),
  onClean: (handler: () => void) => dirtyEmitter.on('clean', handler),
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

  it('emits dirty transitions through onDirty', () => {
    const { onDirty } = useDraftDirty()
    const handler = vi.fn()
    onDirty(handler)

    expect(handler).not.toHaveBeenCalled()

    mockWorkbench.markDirty()
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('emits clean transitions through onClean', () => {
    mockWorkbench.state.value.isDirty = true
    const { onClean } = useDraftDirty()
    const handler = vi.fn()
    onClean(handler)

    mockWorkbench.markClean()
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('stops delivering events after the returned unsubscribe runs', () => {
    const { onDirty } = useDraftDirty()
    const handler = vi.fn()
    const stop = onDirty(handler)

    mockWorkbench.markDirty()
    expect(handler).toHaveBeenCalledTimes(1)

    stop()
    mockWorkbench.markClean()
    mockWorkbench.markDirty()
    expect(handler).toHaveBeenCalledTimes(1)
  })
})
