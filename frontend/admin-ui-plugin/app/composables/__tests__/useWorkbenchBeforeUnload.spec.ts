import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'

import { __getBeforeRouteLeaveGuard } from '../../../vitest.vue-router-stub'
import { useWorkbenchBeforeUnload } from '../useWorkbenchBeforeUnload'

// --- T682: dirty-guard contract of the workbench's two "unsaved changes" exits ---
// Harness: mount a minimal component so the composable's onMounted /
// onBeforeUnmount hooks run against a real instance, and grab the route-leave
// guard captured by the vue-router stub (see dsl-workbench.spec.ts).
const mounted: Array<{ unmount: () => void }> = []

function mountHarness(initialDirty = false) {
  const isDirty = ref(initialDirty)
  const wrapper = mount(
    defineComponent({
      setup() {
        useWorkbenchBeforeUnload({ isDirty })
        return () => h('div')
      },
    }),
  )
  // Track every mount: an un-unmounted wrapper keeps its beforeunload listener
  // registered and would pollute later dispatches.
  mounted.push(wrapper)
  return { wrapper, isDirty }
}

function dispatchBeforeUnload(): BeforeUnloadEvent {
  const event = new Event('beforeunload') as BeforeUnloadEvent
  window.dispatchEvent(event)
  return event
}

describe('useWorkbenchBeforeUnload', () => {
  let confirmSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    confirmSpy = vi.spyOn(window, 'confirm') as unknown as ReturnType<typeof vi.spyOn>
  })

  afterEach(() => {
    confirmSpy.mockRestore()
    while (mounted.length > 0) {
      mounted.pop()?.unmount()
    }
  })

  it('beforeunload with dirty state: preventDefault + returnValue warning', () => {
    mountHarness(true)

    const event = new Event('beforeunload') as BeforeUnloadEvent
    const preventSpy = vi.spyOn(event, 'preventDefault')
    window.dispatchEvent(event)

    expect(preventSpy).toHaveBeenCalledTimes(1)
    expect(event.returnValue).toBe('')
  })

  it('beforeunload with clean state: no preventDefault, no returnValue write', () => {
    mountHarness(false)

    const event = new Event('beforeunload') as BeforeUnloadEvent
    const preventSpy = vi.spyOn(event, 'preventDefault')
    window.dispatchEvent(event)

    expect(preventSpy).not.toHaveBeenCalled()
    expect(event.returnValue).toBeUndefined()
  })

  it('beforeunload reacts to isDirty toggling after mount', () => {
    const { isDirty } = mountHarness(false)
    isDirty.value = true

    const event = dispatchBeforeUnload()
    expect(event.returnValue).toBe('')

    isDirty.value = false
    const cleanEvent = new Event('beforeunload') as BeforeUnloadEvent
    const preventSpy = vi.spyOn(cleanEvent, 'preventDefault')
    window.dispatchEvent(cleanEvent)
    expect(preventSpy).not.toHaveBeenCalled()
  })

  it('route-leave guard: dirty + confirm dismissed blocks navigation', () => {
    const { isDirty } = mountHarness()
    isDirty.value = true
    confirmSpy.mockReturnValueOnce(false)

    const guard = __getBeforeRouteLeaveGuard()
    expect(guard).toBeTypeOf('function')
    expect(guard?.()).toBe(false)
    expect(confirmSpy).toHaveBeenCalledWith('You have unsaved changes. Leave anyway?')
  })

  it('route-leave guard: dirty + confirm accepted allows navigation', () => {
    const { isDirty } = mountHarness()
    isDirty.value = true
    confirmSpy.mockReturnValueOnce(true)

    const guard = __getBeforeRouteLeaveGuard()
    expect(guard?.()).toBe(true)
  })

  it('route-leave guard: clean state allows navigation without prompting', () => {
    mountHarness(false)

    const guard = __getBeforeRouteLeaveGuard()
    expect(guard?.()).toBe(true)
    expect(confirmSpy).not.toHaveBeenCalled()
  })

  it('lifecycle: listener added on mount, removed on unmount', () => {
    // afterEach would double-unmount; this test manages its own wrapper.
    mounted.pop()?.unmount()
    const addSpy = vi.spyOn(window, 'addEventListener')
    const removeSpy = vi.spyOn(window, 'removeEventListener')

    const { wrapper } = mountHarness()

    expect(addSpy).toHaveBeenCalledWith('beforeunload', expect.any(Function))

    const listener = addSpy.mock.calls.find(([type]) => type === 'beforeunload')?.[1]
    expect(listener).toBeTypeOf('function')

    wrapper.unmount()
    expect(removeSpy).toHaveBeenCalledWith('beforeunload', listener)

    // after unmount the guard listener is gone: dispatching does nothing
    const event = new Event('beforeunload') as BeforeUnloadEvent
    const preventSpy = vi.spyOn(event, 'preventDefault')
    window.dispatchEvent(event)
    expect(preventSpy).not.toHaveBeenCalled()

    addSpy.mockRestore()
    removeSpy.mockRestore()
  })
})
