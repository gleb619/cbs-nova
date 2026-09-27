import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../useDslApi', () => {
  const fetchPromotionEnvironments = vi.fn()
  const fetchPromotionDefinitions = vi.fn()
  const promoteDefinitions = vi.fn()
  return {
    useDslApi: () => ({
      fetchPromotionEnvironments,
      fetchPromotionDefinitions,
      promoteDefinitions,
    }),
  }
})

vi.mock('../useClientLogger', () => {
  const error = vi.fn()
  const info = vi.fn()
  const warn = vi.fn()
  return {
    useClientLogger: () => ({ error, info, warn }),
  }
})

// eslint-disable-next-line import/first
import { usePromotion } from '../usePromotion'
// eslint-disable-next-line import/first
import * as loggerModule from '../useClientLogger'
// eslint-disable-next-line import/first
import * as apiModule from '../useDslApi'
// eslint-disable-next-line import/first
import type {
  PromoteResult,
  PromotionDefinition,
  PromotionEnvironment,
} from '@cbs/components'

type ApiMock = {
  fetchPromotionEnvironments: ReturnType<typeof vi.fn>
  fetchPromotionDefinitions: ReturnType<typeof vi.fn>
  promoteDefinitions: ReturnType<typeof vi.fn>
}

type LoggerMock = {
  error: ReturnType<typeof vi.fn>
  info: ReturnType<typeof vi.fn>
  warn: ReturnType<typeof vi.fn>
}

const getApiMocks = (): ApiMock =>
  (apiModule as unknown as { useDslApi: () => ApiMock }).useDslApi()

const getLoggerMocks = (): LoggerMock =>
  (loggerModule as unknown as { useClientLogger: () => LoggerMock }).useClientLogger()

function environment(name: string): PromotionEnvironment {
  return { name }
}

function definition(name: string): PromotionDefinition {
  return { name, type: 'WORKFLOW', status: 'ACTIVE' }
}

function promoteResult(dryRun: boolean, published = 2): PromoteResult {
  return {
    dryRun,
    reloaded: true,
    published,
    failed: 0,
    results: [
      { name: 'LoanDsl', outcome: dryRun ? 'unchanged' : 'published', message: undefined },
    ],
  }
}

describe('usePromotion', () => {
  beforeEach(() => {
    const api = getApiMocks()
    api.fetchPromotionEnvironments.mockReset()
    api.fetchPromotionDefinitions.mockReset()
    api.promoteDefinitions.mockReset()
    const logger = getLoggerMocks()
    logger.error.mockReset()
    logger.info.mockReset()
    logger.warn.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  describe('initial state', () => {
    it('starts empty with no loading flags and no errors', () => {
      const {
        environments,
        environmentsLoading,
        environmentsError,
        definitions,
        definitionsLoading,
        definitionsError,
        preview,
        previewLoading,
        previewError,
        applying,
        applyError,
        applyResult,
      } = usePromotion()

      expect(environments.value).toEqual([])
      expect(environmentsLoading.value).toBe(false)
      expect(environmentsError.value).toBeNull()

      expect(definitions.value).toEqual([])
      expect(definitionsLoading.value).toBe(false)
      expect(definitionsError.value).toBeNull()

      expect(preview.value).toBeNull()
      expect(previewLoading.value).toBe(false)
      expect(previewError.value).toBeNull()

      expect(applying.value).toBe(false)
      expect(applyError.value).toBeNull()
      expect(applyResult.value).toBeNull()
    })
  })

  describe('loadEnvironments', () => {
    it('toggles loading true→false and populates environments on success', async () => {
      const api = getApiMocks()
      const envs = [environment('dev'), environment('staging'), environment('prod')]
      let resolve: (v: unknown) => void = () => {}
      api.fetchPromotionEnvironments.mockReturnValue(
        new Promise<unknown>((r) => {
          resolve = r
        }),
      )

      const { environments, environmentsLoading, loadEnvironments } = usePromotion()

      const p = loadEnvironments()
      expect(environmentsLoading.value).toBe(true)

      resolve(envs)
      await p

      expect(api.fetchPromotionEnvironments).toHaveBeenCalledTimes(1)
      expect(environments.value).toEqual(envs)
      expect(environmentsLoading.value).toBe(false)
    })

    it('extracts api error, logs once and resets environments on rejection', async () => {
      const api = getApiMocks()
      const logger = getLoggerMocks()
      const err = Object.assign(new Error('boom'), { data: { message: 'fallback ignored' } })
      // First success populates so we can verify reset clears it.
      api.fetchPromotionEnvironments.mockResolvedValueOnce([
        environment('dev'),
        environment('prod'),
      ])
      api.fetchPromotionEnvironments.mockRejectedValueOnce(err)

      const { environments, environmentsError, environmentsLoading, loadEnvironments } =
        usePromotion()

      await loadEnvironments()
      expect(environments.value).toHaveLength(2)

      await loadEnvironments()

      expect(logger.error).toHaveBeenCalledTimes(1)
      expect(logger.error).toHaveBeenCalledWith('failed to load promotion environments', {
        error: 'fallback ignored',
      })
      expect(environmentsError.value).toBe('fallback ignored')
      expect(environments.value).toEqual([])
      expect(environmentsLoading.value).toBe(false)
    })
  })

  describe('loadDefinitions', () => {
    it('toggles loading true→false and populates definitions on success', async () => {
      const api = getApiMocks()
      const defs = [definition('LoanDsl'), definition('PaymentDsl')]
      let resolve: (v: unknown) => void = () => {}
      api.fetchPromotionDefinitions.mockReturnValue(
        new Promise<unknown>((r) => {
          resolve = r
        }),
      )

      const { definitions, definitionsLoading, loadDefinitions } = usePromotion()

      const p = loadDefinitions('dev')
      expect(definitionsLoading.value).toBe(true)

      resolve(defs)
      await p

      expect(api.fetchPromotionDefinitions).toHaveBeenCalledWith('dev')
      expect(definitions.value).toEqual(defs)
      expect(definitionsLoading.value).toBe(false)
    })

    it('extracts api error, logs once and resets definitions on rejection', async () => {
      const api = getApiMocks()
      const logger = getLoggerMocks()
      api.fetchPromotionDefinitions.mockResolvedValueOnce([definition('Stale')])
      api.fetchPromotionDefinitions.mockRejectedValueOnce({
        data: { message: 'forbidden' },
      })

      const { definitions, definitionsError, definitionsLoading, loadDefinitions } = usePromotion()

      await loadDefinitions('staging')
      expect(definitions.value).toEqual([definition('Stale')])

      await loadDefinitions('prod')

      expect(logger.error).toHaveBeenCalledTimes(1)
      expect(logger.error).toHaveBeenCalledWith('failed to load promotion definitions', {
        error: 'forbidden',
      })
      expect(definitionsError.value).toBe('forbidden')
      expect(definitions.value).toEqual([])
      expect(definitionsLoading.value).toBe(false)
    })
  })

  describe('runPreview', () => {
    it('sets preview, clears error and returns the result on success', async () => {
      const api = getApiMocks()
      const result = promoteResult(true, 0)
      api.promoteDefinitions.mockResolvedValue(result)

      const { preview, previewError, previewLoading, runPreview } = usePromotion()

      const p = runPreview({ source: 'dev', target: 'staging' })
      expect(previewLoading.value).toBe(true)

      const ret = await p

      expect(api.promoteDefinitions).toHaveBeenCalledWith(
        { source: 'dev', target: 'staging' },
        true,
      )
      expect(ret).toEqual(result)
      expect(preview.value).toEqual(result)
      expect(previewError.value).toBeNull()
      expect(previewLoading.value).toBe(false)
    })

    it('extracts api error, logs once, nulls preview and returns null on rejection', async () => {
      const api = getApiMocks()
      const logger = getLoggerMocks()
      api.promoteDefinitions.mockResolvedValueOnce(promoteResult(true))
      api.promoteDefinitions.mockRejectedValueOnce(new Error('preview failed'))

      const { preview, previewError, previewLoading, runPreview } = usePromotion()

      await runPreview({ source: 'dev', target: 'staging' })
      expect(preview.value).not.toBeNull()

      const ret = await runPreview({ source: 'dev', target: 'prod' })

      expect(ret).toBeNull()
      expect(logger.error).toHaveBeenCalledTimes(1)
      expect(logger.error).toHaveBeenCalledWith('failed to preview promotion', {
        error: 'preview failed',
      })
      expect(previewError.value).toBe('preview failed')
      expect(preview.value).toBeNull()
      expect(previewLoading.value).toBe(false)
    })
  })

  describe('apply', () => {
    it('sets applyResult, clears error and returns the result on success', async () => {
      const api = getApiMocks()
      const result = promoteResult(false, 3)
      api.promoteDefinitions.mockResolvedValue(result)

      const { applying, applyError, applyResult, apply } = usePromotion()

      const p = apply({ source: 'dev', target: 'prod', definitions: ['LoanDsl'] })
      expect(applying.value).toBe(true)

      const ret = await p

      expect(api.promoteDefinitions).toHaveBeenCalledWith(
        { source: 'dev', target: 'prod', definitions: ['LoanDsl'] },
        false,
      )
      expect(ret).toEqual(result)
      expect(applyResult.value).toEqual(result)
      expect(applyError.value).toBeNull()
      expect(applying.value).toBe(false)
    })

    it('extracts api error, logs once, nulls applyResult and returns null on rejection', async () => {
      const api = getApiMocks()
      const logger = getLoggerMocks()
      api.promoteDefinitions.mockResolvedValueOnce(promoteResult(false))
      api.promoteDefinitions.mockRejectedValueOnce(new Error('apply failed'))

      const { applying, applyError, applyResult, apply } = usePromotion()

      await apply({ source: 'dev', target: 'prod' })
      expect(applyResult.value).not.toBeNull()

      const ret = await apply({ source: 'staging', target: 'prod' })

      expect(ret).toBeNull()
      expect(logger.error).toHaveBeenCalledTimes(1)
      expect(logger.error).toHaveBeenCalledWith('failed to apply promotion', {
        error: 'apply failed',
      })
      expect(applyError.value).toBe('apply failed')
      expect(applyResult.value).toBeNull()
      expect(applying.value).toBe(false)
    })
  })

  describe('reset', () => {
    it('clears preview, previewError, applyResult, applyError and leaves environments + definitions alone', async () => {
      const api = getApiMocks()
      api.fetchPromotionEnvironments.mockResolvedValue([environment('dev')])
      api.fetchPromotionDefinitions.mockResolvedValue([definition('LoanDsl')])
      api.promoteDefinitions
        .mockResolvedValueOnce(promoteResult(true))
        .mockResolvedValueOnce(promoteResult(false))

      const {
        environments,
        definitions,
        preview,
        previewError,
        applyResult,
        applyError,
        loadEnvironments,
        loadDefinitions,
        runPreview,
        apply,
        reset,
      } = usePromotion()

      await loadEnvironments()
      await loadDefinitions('dev')
      await runPreview({ source: 'dev', target: 'staging' })
      await apply({ source: 'dev', target: 'prod' })

      expect(environments.value).toHaveLength(1)
      expect(definitions.value).toHaveLength(1)
      expect(preview.value).not.toBeNull()
      expect(applyResult.value).not.toBeNull()

      reset()

      expect(preview.value).toBeNull()
      expect(previewError.value).toBeNull()
      expect(applyResult.value).toBeNull()
      expect(applyError.value).toBeNull()
      // Untouched:
      expect(environments.value).toEqual([environment('dev')])
      expect(definitions.value).toEqual([definition('LoanDsl')])
    })
  })
})
