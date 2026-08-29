import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useDslWorkbench } from '../useDslWorkbench'

// The composable under test imports the real useDslApi module, so the module
// itself must be mocked (the globalThis.useDslApi stub from vitest.setup.ts is
// never consulted by an explicit import).
const { dslApi, useDslApiMock } = vi.hoisted(() => {
  const api = {
    getDefinitions: vi.fn(),
    preview: vi.fn(),
    reload: vi.fn(),
    run: vi.fn(),
    explain: vi.fn(),
    saveDraft: vi.fn(),
    publishDraft: vi.fn(),
    validateConstruct: vi.fn(),
  }
  return { dslApi: api, useDslApiMock: vi.fn(() => api) }
})

vi.mock('@cbs/admin-ui-plugin/composables/useDslApi', () => ({
  useDslApi: useDslApiMock,
}))

type ApiMock = {
  getDefinitions: ReturnType<typeof vi.fn>
  preview: ReturnType<typeof vi.fn>
  reload: ReturnType<typeof vi.fn>
  run: ReturnType<typeof vi.fn>
  explain: ReturnType<typeof vi.fn>
  saveDraft: ReturnType<typeof vi.fn>
  publishDraft: ReturnType<typeof vi.fn>
  validateConstruct: ReturnType<typeof vi.fn>
}

const getApi = (): ApiMock => dslApi

describe('useDslWorkbench', () => {
  beforeEach(() => {
    const api = getApi()
    api.getDefinitions.mockReset()
    api.preview.mockReset()
    api.reload.mockReset()
    api.saveDraft.mockReset()
    api.publishDraft.mockReset()
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
    it('POSTs to api.publishDraft and marks the selected construct status as Published', async () => {
      const api = getApi()
      api.getDefinitions.mockResolvedValueOnce([
        { name: 'c1', type: 'Process' as const, status: 'Valid' as const },
        { name: 'c2', type: 'Helper' as const, status: 'Draft' as const },
      ])
      api.publishDraft.mockResolvedValueOnce({ ok: true })

      const wb = useDslWorkbench()
      await wb.loadConstructs()

      wb.selectConstruct('c2')

      await wb.publishConstruct()

      expect(api.publishDraft).toHaveBeenCalledWith('c2', expect.objectContaining({
        name: 'c2',
        status: 'Published',
      }))
      const c1 = wb.state.value.constructs.find((c) => c.name === 'c1')
      const c2 = wb.state.value.constructs.find((c) => c.name === 'c2')
      expect(c1?.status).toBe('Valid')
      expect(c2?.status).toBe('Published')
      expect(wb.state.value.isSaving).toBe(false)
    })

    it('no-op when no construct is selected', async () => {
      const wb = useDslWorkbench()
      await wb.publishConstruct()
      expect(getApi().publishDraft).not.toHaveBeenCalled()
      expect(wb.state.value.isSaving).toBe(false)
    })
  })

  describe('saveConstruct', () => {
    it('POSTs the selected construct to api.saveDraft and clears isDirty', async () => {
      const api = getApi()
      api.getDefinitions.mockResolvedValueOnce([
        { name: 'c1', type: 'Process' as const, status: 'Draft' as const, version: '1.0' },
      ])
      api.saveDraft.mockResolvedValueOnce({ ok: true })

      const wb = useDslWorkbench()
      await wb.loadConstructs()
      wb.markDirty()

      await wb.saveConstruct()

      expect(api.saveDraft).toHaveBeenCalledWith('c1', expect.objectContaining({
        name: 'c1',
        status: 'Draft',
      }))
      expect(wb.state.value.isDirty).toBe(false)
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
