import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { useLoggerSpy } = vi.hoisted(() => ({
  useLoggerSpy: vi.fn((_scope: string) => ({
    trace: vi.fn(),
    debug: vi.fn(),
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
  })),
}))

vi.mock('@cbs/components/composables', () => ({
  useLogger: useLoggerSpy,
}))

import { useClientLogger } from '../useClientLogger'

describe('useClientLogger', () => {
  let originalWindow: unknown

  beforeEach(() => {
    originalWindow = globalThis.window
    globalThis.window = originalWindow
    useLoggerSpy.mockClear()
  })

  afterEach(() => {
    globalThis.window = originalWindow
  })

  it('returns a noop logger when window is undefined without calling useLogger', () => {
    delete (globalThis as { window?: unknown }).window
    const logger = useClientLogger('any-scope')

    expect(typeof logger.trace).toBe('function')
    expect(typeof logger.debug).toBe('function')
    expect(typeof logger.info).toBe('function')
    expect(typeof logger.warn).toBe('function')
    expect(typeof logger.error).toBe('function')

    logger.trace('msg')
    logger.debug('msg')
    logger.info('msg')
    logger.warn('msg')
    logger.error('msg')

    expect(useLoggerSpy).not.toHaveBeenCalled()
  })

  it('delegates to useLogger when window is defined, passing scope and returning its result', () => {
    const mockLogger = {
      trace: vi.fn(),
      debug: vi.fn(),
      info: vi.fn(),
      warn: vi.fn(),
      error: vi.fn(),
    }
    useLoggerSpy.mockReturnValueOnce(mockLogger)

    const logger = useClientLogger('my-scope')

    expect(useLoggerSpy).toHaveBeenCalledTimes(1)
    expect(useLoggerSpy).toHaveBeenCalledWith('my-scope')
    expect(logger).toBe(mockLogger)
  })

  it('returns the same noop logger instance across SSR calls', () => {
    delete (globalThis as { window?: unknown }).window

    const first = useClientLogger('first')
    const second = useClientLogger('second')

    expect(first).toBe(second)
    expect(useLoggerSpy).not.toHaveBeenCalled()
  })
})
