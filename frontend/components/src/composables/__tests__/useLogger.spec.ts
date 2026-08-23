import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { type LogLevel, useLogger } from '../useLogger'

describe('useLogger', () => {
  let spies: Record<LogLevel, ReturnType<typeof vi.spyOn>>

  beforeEach(() => {
    window.localStorage.clear()
    spies = {
      debug: vi.spyOn(console, 'debug').mockImplementation(() => {}),
      info: vi.spyOn(console, 'info').mockImplementation(() => {}),
      warn: vi.spyOn(console, 'warn').mockImplementation(() => {}),
      error: vi.spyOn(console, 'error').mockImplementation(() => {}),
    }
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('logs messages at or above the configured minimum level', () => {
    const logger = useLogger('test', { minLevel: 'info' })

    logger.debug('hidden')
    logger.info('shown')
    logger.warn('also shown')

    expect(spies.debug).not.toHaveBeenCalled()
    expect(spies.info).toHaveBeenCalledWith('[test] shown')
    expect(spies.warn).toHaveBeenCalledWith('[test] also shown')
  })

  it('includes structured data when provided', () => {
    const logger = useLogger('test', { minLevel: 'debug' })

    logger.info('event', { id: 1 })

    expect(spies.info).toHaveBeenCalledWith('[test] event', { id: 1 })
  })

  it('reads the minimum level from localStorage when present', () => {
    window.localStorage.setItem('cbs-log-level', 'error')

    const logger = useLogger('test')

    logger.warn('hidden')
    logger.error('shown')

    expect(spies.warn).not.toHaveBeenCalled()
    expect(spies.error).toHaveBeenCalledWith('[test] shown')
  })

  it('uses the options level over localStorage', () => {
    window.localStorage.setItem('cbs-log-level', 'error')

    const logger = useLogger('test', { minLevel: 'debug' })

    logger.debug('shown')

    expect(spies.debug).toHaveBeenCalledWith('[test] shown')
  })
})
