import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useLivePollingPreference } from '../useLivePollingPreference'

// useLocalStorageState reads window.localStorage.getItem synchronously on
// import time. The vitest setup does not install a localStorage mock, so
// each spec installs a fresh controllable one.
let storage: Record<string, string> = {}

function installLocalStorageMock() {
  storage = {}
  const target = typeof window !== 'undefined' ? window : globalThis
  Object.defineProperty(target, 'localStorage', {
    value: {
      getItem: vi.fn((key: string) => storage[key] ?? null),
      setItem: vi.fn((key: string, value: string) => {
        storage[key] = value
      }),
      removeItem: vi.fn((key: string) => {
        delete storage[key]
      }),
    },
    writable: true,
    configurable: true,
  })
}

const flush = async () => {
  await vi.advanceTimersByTimeAsync(0)
  await Promise.resolve()
}

interface SpyPair {
  start: ReturnType<typeof vi.fn>
  stop: ReturnType<typeof vi.fn>
}

function buildSpyPair(): Promise<SpyPair> {
  return Promise.resolve({ start: vi.fn(), stop: vi.fn() })
}

describe('useLivePollingPreference', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    installLocalStorageMock()
  })

  it('starts in the default state when no value is persisted', () => {
    const active = vi.fn(() => true)
    const start = vi.fn()
    const stop = vi.fn()

    const { enabled, intervalMs } = useLivePollingPreference({
      storagePrefix: 'spec.toggleDefaults',
      defaultIntervalMs: 5000,
      defaultEnabled: false,
      isActive: active,
      start,
      stop,
    })

    expect(enabled.value).toBe(false)
    expect(intervalMs.value).toBe(5000)
    expect(active).not.toHaveBeenCalled()
    expect(start).not.toHaveBeenCalled()
    expect(stop).not.toHaveBeenCalled()
  })

  it('honours a persisted enabled value and interval', () => {
    storage['spec.persisted.enabled'] = 'true'
    storage['spec.persisted.intervalMs'] = '10000'

    const { enabled, intervalMs } = useLivePollingPreference({
      storagePrefix: 'spec.persisted',
      defaultIntervalMs: 5000,
      defaultEnabled: false,
      isActive: () => true,
      start: vi.fn(),
      stop: vi.fn(),
    })

    expect(enabled.value).toBe(true)
    expect(intervalMs.value).toBe(10000)
  })

  it('starts polling when toggled on while active', () => {
    const { onToggle, enabled } = useLivePollingPreference({
      storagePrefix: 'spec.toggleOn',
      defaultIntervalMs: 5000,
      defaultEnabled: false,
      isActive: () => true,
      start: vi.fn(),
      stop: vi.fn(),
    })

    onToggle(true)
    expect(enabled.value).toBe(true)
    expect(storage['spec.toggleOn.enabled']).toBe('true')
  })

  it('does not start polling when toggled on while inactive', () => {
    const start = vi.fn()
    const stop = vi.fn()
    const { onToggle, enabled } = useLivePollingPreference({
      storagePrefix: 'spec.toggleOnInactive',
      defaultIntervalMs: 5000,
      defaultEnabled: false,
      isActive: () => false,
      start,
      stop,
    })

    onToggle(true)
    expect(enabled.value).toBe(true)
    expect(start).not.toHaveBeenCalled()
    expect(stop).toHaveBeenCalledTimes(1)
  })

  it('stops polling when toggled off and persists the new value', () => {
    storage['spec.toggleOff.enabled'] = 'true'

    const start = vi.fn()
    const stop = vi.fn()
    const { onToggle, enabled } = useLivePollingPreference({
      storagePrefix: 'spec.toggleOff',
      defaultIntervalMs: 5000,
      defaultEnabled: true,
      isActive: () => true,
      start,
      stop,
    })

    expect(enabled.value).toBe(true)
    onToggle(false)
    expect(enabled.value).toBe(false)
    expect(stop).toHaveBeenCalledTimes(1)
    expect(storage['spec.toggleOff.enabled']).toBe('false')
  })

  it('restarts polling with the current interval on interval change when enabled and active', () => {
    const start = vi.fn()
    const stop = vi.fn()
    const { onIntervalChange, intervalMs } = useLivePollingPreference({
      storagePrefix: 'spec.intervalChange',
      defaultIntervalMs: 5000,
      defaultEnabled: true,
      isActive: () => true,
      start,
      stop,
    })

    intervalMs.value = 10000
    start.mockClear()
    stop.mockClear()

    onIntervalChange()
    expect(stop).toHaveBeenCalledTimes(1)
    expect(start).toHaveBeenCalledTimes(1)
    expect(start).toHaveBeenCalledWith(10000)
    expect(storage['spec.intervalChange.intervalMs']).toBe('10000')
  })

  it('is a no-op on interval change when disabled', () => {
    storage['spec.intervalDisabled.enabled'] = 'false'

    const start = vi.fn()
    const stop = vi.fn()
    const { onIntervalChange, intervalMs } = useLivePollingPreference({
      storagePrefix: 'spec.intervalDisabled',
      defaultIntervalMs: 5000,
      defaultEnabled: false,
      isActive: () => true,
      start,
      stop,
    })

    intervalMs.value = 15000
    onIntervalChange()
    expect(stop).not.toHaveBeenCalled()
    expect(start).not.toHaveBeenCalled()
  })

  it('is a no-op on interval change when inactive (predicate returns false)', () => {
    storage['spec.intervalInactive.enabled'] = 'true'

    const start = vi.fn()
    const stop = vi.fn()
    const active = vi.fn(() => false)
    const { onIntervalChange } = useLivePollingPreference({
      storagePrefix: 'spec.intervalInactive',
      defaultIntervalMs: 5000,
      defaultEnabled: true,
      isActive: active,
      start,
      stop,
    })

    onIntervalChange()
    expect(active).toHaveBeenCalled()
    expect(stop).not.toHaveBeenCalled()
    expect(start).not.toHaveBeenCalled()
  })

  it('persists both enabled and interval across composable instantiations', async () => {
    const first = useLivePollingPreference({
      storagePrefix: 'spec.persistence',
      defaultIntervalMs: 5000,
      defaultEnabled: false,
      isActive: () => true,
      start: vi.fn(),
      stop: vi.fn(),
    })

    first.onToggle(true)
    first.intervalMs.value = 30000
    await flush()

    const second = useLivePollingPreference({
      storagePrefix: 'spec.persistence',
      defaultIntervalMs: 5000,
      defaultEnabled: false,
      isActive: () => true,
      start: vi.fn(),
      stop: vi.fn(),
    })

    expect(second.enabled.value).toBe(true)
    expect(second.intervalMs.value).toBe(30000)
    expect(storage['spec.persistence.enabled']).toBe('true')
    expect(storage['spec.persistence.intervalMs']).toBe('30000')
  })

  it('uses the start wrapper with the bound id-equivalent semantics (start receives the interval)', async () => {
    const { start, stop } = await buildSpyPair()
    const { onIntervalChange, intervalMs, onToggle } = useLivePollingPreference({
      storagePrefix: 'spec.bindId',
      defaultIntervalMs: 5000,
      defaultEnabled: false,
      isActive: () => true,
      start,
      stop,
    })

    intervalMs.value = 7500
    onToggle(true)
    expect(start).toHaveBeenCalledWith(7500)

    start.mockClear()
    stop.mockClear()
    intervalMs.value = 15000
    onIntervalChange()
    expect(stop).toHaveBeenCalledTimes(1)
    expect(start).toHaveBeenCalledWith(15000)
  })

  it('reflects a reactive isActive predicate that flips while toggled on', async () => {
    const start = vi.fn()
    const stop = vi.fn()
    const active = ref(true)
    const { onIntervalChange } = useLivePollingPreference({
      storagePrefix: 'spec.reactiveActive',
      defaultIntervalMs: 5000,
      defaultEnabled: true,
      isActive: () => active.value,
      start,
      stop,
    })

    active.value = false
    onIntervalChange()
    expect(stop).not.toHaveBeenCalled()
    expect(start).not.toHaveBeenCalled()

    active.value = true
    onIntervalChange()
    expect(stop).toHaveBeenCalledTimes(1)
    expect(start).toHaveBeenCalledWith(5000)
  })
})