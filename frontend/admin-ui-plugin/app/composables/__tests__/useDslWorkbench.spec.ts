import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useDslWorkbench } from '../useDslWorkbench'

type ApiMock = {
  getDefinitions: ReturnType<typeof vi.fn>
  preview: ReturnType<typeof vi.fn>
  reload: ReturnType<typeof vi.fn>
  run: ReturnType<typeof vi.fn>
  explain: ReturnType<typeof vi.fn>
  saveDraft: ReturnType<typeof vi.fn>
  validateConstruct: ReturnType<typeof vi.fn>
}

const getApi = (): ApiMock => vi.mocked(useDslApi as never)()

describe('useDslWorkbench', () => {
  beforeEach(() => {
    const api = getApi()
    api.getDefinitions.mockReset()
    api.preview.mockReset()
    api.reload.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  describe('loadConstructs', () => {
    it('handles plain array response and auto-selects first construct', async () => {
      const api = getApi()
      const construct = { name: 'c1', type: 'Process' as const, status: 'Draft' as const }
      api.getDefinitions.mockResolvedValueOnce([construct])

      const wb = useDslWorkbench()
      await wb.loadConstructs()

      expect(api.getDefinitions).toHaveBeenCalledWith()
      expect(wb.state.value.constructs).toEqual([construct])
      expect(wb.state.value.selectedName).toBe('c1')
      expect(wb.selectedConstruct.value).toEqual(construct)
      expect(wb.state.value.isLoading).toBe(false)
    })

    it('handles {constructs:[]} response shape', async () => {
      const api = getApi()
      const list = [
        { name: 'c1', type: 'Function' as const, status: 'Valid' as const },
        { name: 'c2', type: 'Helper' as const, status: 'Draft' as const },
      ]
      api.getDefinitions.mockResolvedValueOnce({ constructs: list })

      const wb = useDslWorkbench()
      await wb.loadConstructs()

      expect(wb.state.value.constructs).toEqual(list)
      expect(wb.state.value.selectedName).toBe('c1')
    })

    it('does not overwrite an already selected name when loading more constructs', async () => {
      const api = getApi()
      api.getDefinitions.mockResolvedValueOnce([
        { name: 'a', type: 'Process' as const, status: 'Draft' as const },
        { name: 'b', type: 'Process' as const, status: 'Draft' as const },
      ])

      const wb = useDslWorkbench()
      wb.selectConstruct('b')

      await wb.loadConstructs()

      expect(wb.state.value.selectedName).toBe('b')
    })
  })

  describe('selectConstruct', () => {
    it('updates selectedName and clears validationErrors and isDirty', async () => {
      const api = getApi()
      api.getDefinitions.mockResolvedValueOnce([
        { name: 'c1', type: 'Process' as const, status: 'Draft' as const },
        { name: 'c2', type: 'Helper' as const, status: 'Draft' as const },
      ])

      const wb = useDslWorkbench()
      await wb.loadConstructs()

      // baseline: selectedName auto-picked to first; mark dirty
      wb.markDirty()
      expect(wb.state.value.isDirty).toBe(true)
      expect(wb.state.value.selectedName).toBe('c1')

      wb.selectConstruct('c2')

      expect(wb.state.value.selectedName).toBe('c2')
      expect(wb.state.value.validationErrors).toEqual([])
      expect(wb.state.value.isDirty).toBe(false)
    })
  })

  describe('reloadDefinitions', () => {
    it('calls api.reload then loadConstructs', async () => {
      const api = getApi()
      api.reload.mockResolvedValueOnce({ ok: true })
      api.getDefinitions.mockResolvedValueOnce([])

      const wb = useDslWorkbench()
      await wb.reloadDefinitions()

      const reloadOrder = api.reload.mock.invocationCallOrder[0]
      const loadOrder = api.getDefinitions.mock.invocationCallOrder[0]
      expect(reloadOrder).toBeLessThan(loadOrder)
      expect(api.reload).toHaveBeenCalledWith()
      expect(api.getDefinitions).toHaveBeenCalledWith()
    })
  })

  describe('publishConstruct', () => {
    it('marks the selected construct status as Published', async () => {
      const api = getApi()
      api.getDefinitions.mockResolvedValueOnce([
        { name: 'c1', type: 'Process' as const, status: 'Valid' as const },
        { name: 'c2', type: 'Helper' as const, status: 'Draft' as const },
      ])

      const wb = useDslWorkbench()
      await wb.loadConstructs()

      wb.selectConstruct('c2')

      await wb.publishConstruct()

      const c1 = wb.state.value.constructs.find((c) => c.name === 'c1')
      const c2 = wb.state.value.constructs.find((c) => c.name === 'c2')
      expect(c1?.status).toBe('Valid')
      expect(c2?.status).toBe('Published')
      expect(wb.state.value.isSaving).toBe(false)
    })

    it('no-op when no construct is selected', async () => {
      const wb = useDslWorkbench()
      await wb.publishConstruct()
      expect(getApi().getDefinitions).not.toHaveBeenCalled()
      expect(wb.state.value.isSaving).toBe(false)
    })
  })

  describe('validateConstruct', () => {
    it('populates validationErrors from api.preview', async () => {
      const api = getApi()
      api.getDefinitions.mockResolvedValueOnce([
        { name: 'c1', type: 'Process' as const, status: 'Draft' as const },
      ])
      api.preview.mockResolvedValueOnce({
        errors: [{ field: 'name', message: 'invalid', severity: 'error' as const }],
      })

      const wb = useDslWorkbench()
      await wb.loadConstructs()
      const errors = await wb.validateConstruct()

      expect(api.preview).toHaveBeenCalledWith('c1', {})
      expect(errors).toEqual([{ field: 'name', message: 'invalid', severity: 'error' as const }])
      expect(wb.state.value.validationErrors).toEqual([
        { field: 'name', message: 'invalid', severity: 'error' as const },
      ])
    })
  })
})
