import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useIntervalEmitter } from '../useIntervalEmitter'

describe('useIntervalEmitter', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => false })
  })

  it('emits a tick on every visible interval', async () => {
    const ticker = useIntervalEmitter({ intervalMs: 1000 })
    const handler = vi.fn()
    ticker.onTick(handler)

    ticker.start()
    expect(ticker.running.value).toBe(true)
    expect(vi.getTimerCount()).toBe(1)
    expect(handler).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1000)
    expect(handler).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(2000)
    expect(handler).toHaveBeenCalledTimes(3)

    ticker.stop()
    expect(ticker.running.value).toBe(false)
    expect(vi.getTimerCount()).toBe(0)
  })

  it('does not emit ticks while hidden and fires one on resume', async () => {
    const ticker = useIntervalEmitter({ intervalMs: 1000 })
    const tickHandler = vi.fn()
    const resumeHandler = vi.fn()
    ticker.onTick(tickHandler)
    ticker.onResume(resumeHandler)
    ticker.start()

    await vi.advanceTimersByTimeAsync(1000)
    expect(tickHandler).toHaveBeenCalledTimes(1)

    Object.defineProperty(document, 'hidden', { configurable: true, get: () => true })
    document.dispatchEvent(new Event('visibilitychange'))

    await vi.advanceTimersByTimeAsync(3000)
    expect(tickHandler).toHaveBeenCalledTimes(1)
    expect(resumeHandler).not.toHaveBeenCalled()

    Object.defineProperty(document, 'hidden', { configurable: true, get: () => false })
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(0)

    expect(tickHandler).toHaveBeenCalledTimes(2)
    expect(resumeHandler).toHaveBeenCalledTimes(1)

    ticker.stop()
  })

  it('changes interval and restarts when running', async () => {
    const ticker = useIntervalEmitter({ intervalMs: 1000 })
    const handler = vi.fn()
    ticker.onTick(handler)
    ticker.start()

    await vi.advanceTimersByTimeAsync(1000)
    expect(handler).toHaveBeenCalledTimes(1)

    ticker.setIntervalMs(500)
    await vi.advanceTimersByTimeAsync(500)
    expect(handler).toHaveBeenCalledTimes(2)

    await vi.advanceTimersByTimeAsync(500)
    expect(handler).toHaveBeenCalledTimes(3)

    ticker.stop()
  })

  it('detaches the visibility listener on stop', () => {
    const removeSpy = vi.spyOn(document, 'removeEventListener')
    const ticker = useIntervalEmitter({ intervalMs: 1000 })
    ticker.start()
    ticker.stop()
    expect(removeSpy).toHaveBeenCalledWith('visibilitychange', expect.any(Function))
    removeSpy.mockRestore()
  })

  it('is idempotent when stopped multiple times', () => {
    const ticker = useIntervalEmitter({ intervalMs: 1000 })
    ticker.start()
    expect(vi.getTimerCount()).toBe(1)

    ticker.stop()
    ticker.stop()
    expect(vi.getTimerCount()).toBe(0)
    expect(ticker.running.value).toBe(false)
  })
})
