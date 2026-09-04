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
    deleteDraft: vi.fn(),
    validateConstruct: vi.fn(),
    updateDescription: vi.fn(),
    readDslFile: vi.fn(),
    writeDslFile: vi.fn(),
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
  deleteDraft: ReturnType<typeof vi.fn>
  validateConstruct: ReturnType<typeof vi.fn>
  updateDescription: ReturnType<typeof vi.fn>
  readDslFile: ReturnType<typeof vi.fn>
  writeDslFile: ReturnType<typeof vi.fn>
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
    api.deleteDraft.mockReset()
    api.readDslFile.mockReset()
    api.updateDescription.mockReset()
    api.writeDslFile.mockReset()
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
      api.getDefinitions.mockResolvedValueOnce([
        { name: 'c1', type: 'Process' as const, status: 'Valid' as const },
        { name: 'c2', type: 'Helper' as const, status: 'Published' as const },
      ])
      api.publishDraft.mockResolvedValueOnce({ reloaded: true })

      const wb = useDslWorkbench()
      await wb.loadConstructs()

      wb.selectConstruct('c2')

      await wb.publishConstruct()

      expect(api.publishDraft).toHaveBeenCalledWith(
        'c2',
        expect.objectContaining({
          name: 'c2',
          status: 'Published',
        }),
      )
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

      expect(api.saveDraft).toHaveBeenCalledWith(
        'c1',
        expect.objectContaining({
          name: 'c1',
          status: 'Draft',
        }),
      )
      expect(wb.state.value.isDirty).toBe(false)
      expect(wb.state.value.isSaving).toBe(false)
    })
  })

  describe('saveConstruct file-backed', () => {
    it('writes source file content when selected construct has filePath', async () => {
      const api = getApi()
      api.getDefinitions.mockResolvedValueOnce([
        {
          name: 'c1',
          type: 'Process' as const,
          status: 'Published' as const,
          filePath: 'LoanDsl.java',
        },
      ])
      api.writeDslFile.mockResolvedValueOnce({ ok: true })

      const wb = useDslWorkbench()
      await wb.loadConstructs()
      wb.markDirty()

      await wb.saveConstruct('public class C1 {}')

      expect(api.writeDslFile).toHaveBeenCalledWith('c1', 'public class C1 {}')
      expect(api.saveDraft).not.toHaveBeenCalled()
      expect(wb.state.value.isDirty).toBe(false)
      expect(wb.state.value.isSaving).toBe(false)
    })

    it('falls back to draft save when no filePath is present', async () => {
      const api = getApi()
      api.getDefinitions.mockResolvedValueOnce([
        { name: 'c1', type: 'Process' as const, status: 'Draft' as const },
      ])
      api.saveDraft.mockResolvedValueOnce({ ok: true })

      const wb = useDslWorkbench()
      await wb.loadConstructs()
      wb.markDirty()

      await wb.saveConstruct('some content')

      expect(api.saveDraft).toHaveBeenCalledWith(
        'c1',
        expect.objectContaining({ name: 'c1', status: 'Draft' }),
      )
      expect(api.writeDslFile).not.toHaveBeenCalled()
    })
  })
  describe('deleteConstruct', () => {
    it('DELETEs via api.deleteDraft and reloads the construct list', async () => {
      const api = getApi()
      api.getDefinitions.mockResolvedValueOnce([
        { name: 'c1', type: 'Process' as const, status: 'Draft' as const },
        { name: 'c2', type: 'Helper' as const, status: 'Draft' as const },
      ])
      api.deleteDraft.mockResolvedValueOnce({ ok: true })

      const wb = useDslWorkbench()
      await wb.loadConstructs()
      wb.selectConstruct('c1')
      api.getDefinitions.mockResolvedValueOnce([
        { name: 'c2', type: 'Helper' as const, status: 'Draft' as const },
      ])

      await wb.deleteConstruct('c1')

      expect(api.deleteDraft).toHaveBeenCalledWith('c1')
      expect(api.getDefinitions).toHaveBeenCalledTimes(2)
      expect(wb.state.value.constructs).toEqual([{ name: 'c2', type: 'Helper', status: 'Draft' }])
    })

    it('clears the selected name when the deleted construct was selected', async () => {
      const api = getApi()
      api.getDefinitions.mockResolvedValueOnce([
        { name: 'c1', type: 'Process' as const, status: 'Draft' as const },
        { name: 'c2', type: 'Helper' as const, status: 'Draft' as const },
      ])
      api.deleteDraft.mockResolvedValueOnce({ ok: true })

      const wb = useDslWorkbench()
      await wb.loadConstructs()
      wb.selectConstruct('c1')
      api.getDefinitions.mockResolvedValueOnce([
        { name: 'c2', type: 'Helper' as const, status: 'Draft' as const },
      ])

      await wb.deleteConstruct('c1')

      expect(wb.state.value.selectedName).toBe('c2')
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

  describe('publishConstruct with compile diagnostics', () => {
    it('populates validationErrors and does not mark Published when diagnostics are present', async () => {
      const api = getApi()
      api.getDefinitions.mockResolvedValueOnce([
        { name: 'c2', type: 'Helper' as const, status: 'Draft' as const },
      ])
      api.publishDraft.mockResolvedValueOnce({
        diagnostics: [
          {
            file: '/tmp/Bad.java',
            line: 5,
            column: 10,
            message: 'type mismatch',
            severity: 'error',
          },
        ],
        reloadError: 'Failed to compile DSL source: Bad.java',
      })

      const wb = useDslWorkbench()
      await wb.loadConstructs()
      wb.selectConstruct('c2')
      await wb.publishConstruct()

      expect(api.publishDraft).toHaveBeenCalledWith(
        'c2',
        expect.objectContaining({
          name: 'c2',
          status: 'Published',
        }),
      )
      const c2 = wb.state.value.constructs.find((c) => c.name === 'c2')
      expect(c2?.status).toBe('Draft')
      expect(wb.state.value.validationErrors).toEqual([
        { field: 'Bad.java:5', message: 'type mismatch', severity: 'error' as const },
      ])
    })

    it('clears validationErrors on a clean publish', async () => {
      const api = getApi()
      api.getDefinitions.mockResolvedValueOnce([
        { name: 'c2', type: 'Helper' as const, status: 'Draft' as const },
      ])
      api.getDefinitions.mockResolvedValueOnce([
        { name: 'c2', type: 'Helper' as const, status: 'Published' as const },
      ])
      api.publishDraft.mockResolvedValueOnce({ reloaded: true })

      const wb = useDslWorkbench()
      await wb.loadConstructs()
      wb.selectConstruct('c2')
      wb.state.value.validationErrors = [
        { field: 'old', message: 'old', severity: 'error' as const },
      ]

      await wb.publishConstruct()

      const c2 = wb.state.value.constructs.find((c) => c.name === 'c2')
      expect(c2?.status).toBe('Published')
      expect(wb.state.value.validationErrors).toEqual([])
    })
  })

  describe('reloadDefinitions with compile diagnostics', () => {
    it('populates validationErrors when the backend returns diagnostics', async () => {
      const api = getApi()
      const reloadError = {
        data: {
          diagnostics: [
            { file: '/tmp/Broken.java', line: 2, message: 'missing semicolon', severity: 'error' },
          ],
        },
      }
      api.reload.mockRejectedValueOnce(reloadError)

      const wb = useDslWorkbench()
      await expect(wb.reloadDefinitions()).rejects.toEqual(reloadError)

      expect(wb.state.value.validationErrors).toEqual([
        { field: 'Broken.java:2', message: 'missing semicolon', severity: 'error' as const },
      ])
    })
  })

  describe('markClean', () => {
    it('clears isDirty without calling the backend', () => {
      const wb = useDslWorkbench()
      wb.markDirty()
      expect(wb.state.value.isDirty).toBe(true)

      wb.markClean()

      expect(wb.state.value.isDirty).toBe(false)
      expect(dslApi.saveDraft).not.toHaveBeenCalled()
    })
  })
})

describe('normalizeConstruct', () => {
  it('preserves all fields returned by the backend', async () => {
    const api = getApi()
    api.getDefinitions.mockResolvedValueOnce([
      {
        name: 'BatchProcessing',
        type: 'Process',
        status: 'Published',
        version: 'v1',
        taskQueue: 'BatchProcessing-queue',
        inputType: 'BatchIn',
        outputType: 'BatchOut',
        hasCompensation: false,
        description: 'Summarizes a batch of items.',
        filePath: 'dsl/BatchProcessingDsl.java',
      },
    ])

    const wb = useDslWorkbench()
    await wb.loadConstructs()

    expect(wb.selectedConstruct.value).toEqual({
      name: 'BatchProcessing',
      type: 'Process',
      status: 'Published',
      version: 'v1',
      taskQueue: 'BatchProcessing-queue',
      inputType: 'BatchIn',
      outputType: 'BatchOut',
      hasCompensation: false,
      description: 'Summarizes a batch of items.',
      filePath: 'dsl/BatchProcessingDsl.java',
    })
  })
})
