import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { trace, logger, useClientLoggerMock } = vi.hoisted(() => {
  const trace = vi.fn()
  return {
    trace,
    logger: {
      trace,
      debug: vi.fn(),
      info: vi.fn(),
      warn: vi.fn(),
      error: vi.fn(),
    },
    useClientLoggerMock: vi.fn(() => logger),
  }
})

vi.mock('@cbs/admin-ui-plugin/composables/useClientLogger', () => ({
  useClientLogger: useClientLoggerMock,
}))

vi.stubGlobal('defineNuxtPlugin', (fn: unknown) => fn)

const { default: logNuxtLifecycle } = await import('../logNuxtLifecycle.client')

describe('logNuxtLifecycle plugin', () => {
  let nowSpy: ReturnType<typeof vi.spyOn>
  let nowSequence: number[]

  beforeEach(() => {
    nowSequence = []
    nowSpy = vi.spyOn(performance, 'now').mockImplementation(() => {
      const next = nowSequence.shift()
      if (next === undefined) {
        throw new Error('performance.now mock sequence exhausted')
      }
      return next
    })
    trace.mockClear()
    useClientLoggerMock.mockClear()
  })

  afterEach(() => {
    nowSpy.mockRestore()
  })

  const installPlugin = () => {
    const hooks: {
      beforeEach?: (event: { name: string }) => void
      afterEach?: (event: { name: string; args: unknown[] }) => void
    } = {}

    const nuxtApp = {
      hooks: {
        beforeEach: (cb: typeof hooks.beforeEach) => {
          hooks.beforeEach = cb
        },
        afterEach: (cb: typeof hooks.afterEach) => {
          hooks.afterEach = cb
        },
      },
    }

    logNuxtLifecycle(nuxtApp)

    if (!hooks.beforeEach || !hooks.afterEach) {
      throw new Error('Expected both beforeEach and afterEach hooks to be registered')
    }

    return hooks
  }

  it('ignores non-lifecycle names and does not interfere with later matched pairs', () => {
    const hooks = installPlugin()
    const foreignArgs: unknown[] = ['ignored']

    nowSequence = [999]
    hooks.beforeEach({ name: 'not:a:lifecycle' })
    hooks.afterEach({ name: 'not:a:lifecycle', args: foreignArgs })

    expect(trace).not.toHaveBeenCalled()
    expect(nowSpy).not.toHaveBeenCalled()

    nowSequence = [1000, 1005]
    hooks.beforeEach({ name: 'app:mounted' })
    hooks.afterEach({ name: 'app:mounted', args: ['x'] })

    expect(trace).toHaveBeenCalledTimes(1)
    expect(trace).toHaveBeenCalledWith('app:mounted: 5.000ms', ['x'])
  })

  it('logs a single matched lifecycle pair with its duration and forwarded args', () => {
    const hooks = installPlugin()

    nowSequence = [2000, 2012.345]
    hooks.beforeEach({ name: 'page:finish' })
    hooks.afterEach({ name: 'page:finish', args: ['arg1', { nested: true }] })

    expect(trace).toHaveBeenCalledTimes(1)
    expect(trace).toHaveBeenCalledWith('page:finish: 12.345ms', ['arg1', { nested: true }])
  })

  it('handles nested pairs using FIFO starts and removes the map entry when drained', () => {
    const hooks = installPlugin()

    nowSequence = [3000, 3007, 3020, 3035]

    hooks.beforeEach({ name: 'app:mounted' })
    hooks.beforeEach({ name: 'app:mounted' })
    hooks.afterEach({ name: 'app:mounted', args: ['first'] })
    hooks.afterEach({ name: 'app:mounted', args: ['second'] })

    expect(trace).toHaveBeenCalledTimes(2)
    expect(trace).toHaveBeenNthCalledWith(1, 'app:mounted: 20.000ms', ['first'])
    expect(trace).toHaveBeenNthCalledWith(2, 'app:mounted: 28.000ms', ['second'])

    // After the stack is drained, a fresh pair behaves the same way and does
    // not reuse any stale state (the map entry has been deleted).
    trace.mockClear()
    nowSpy.mockClear()
    nowSequence = [4000, 4003]

    hooks.beforeEach({ name: 'app:mounted' })
    hooks.afterEach({ name: 'app:mounted', args: ['fresh'] })

    expect(trace).toHaveBeenCalledTimes(1)
    expect(trace).toHaveBeenCalledWith('app:mounted: 3.000ms', ['fresh'])
    expect(nowSpy).toHaveBeenCalledTimes(2)
  })

  it('logs an unmatched after hook with ?? duration', () => {
    const hooks = installPlugin()

    nowSequence = [5000]
    hooks.afterEach({ name: 'app:beforeMount', args: ['no-before'] })

    expect(trace).toHaveBeenCalledTimes(1)
    expect(trace).toHaveBeenCalledWith('app:beforeMount: ??', ['no-before'])
  })
})
