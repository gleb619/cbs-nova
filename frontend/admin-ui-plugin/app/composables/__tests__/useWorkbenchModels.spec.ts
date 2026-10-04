import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useWorkbenchModels } from '../useWorkbenchModels'

describe('useWorkbenchModels', () => {
  const readModels = vi.fn<(name: string) => Promise<string | null>>()
  const writeModels = vi.fn<(name: string, content: string) => Promise<unknown>>()
  const logError = vi.fn<(message: string, fields?: Record<string, unknown>) => void>()

  beforeEach(() => {
    readModels.mockReset()
    writeModels.mockReset()
    logError.mockReset()
    readModels.mockResolvedValue(null)
    writeModels.mockResolvedValue({ ok: true })
  })

  it('loads an existing model file and starts clean', async () => {
    readModels.mockResolvedValueOnce('public class Foo {}')
    const m = useWorkbenchModels({ readModels, writeModels, logError })

    await m.loadModels('Foo')

    expect(readModels).toHaveBeenCalledWith('Foo')
    expect(m.models.value).toBe('public class Foo {}')
    expect(m.dirty.value).toBe(false)
  })

  it('starts blank (no auto-seed) when the model file does not yet exist', async () => {
    readModels.mockResolvedValueOnce(null)
    const m = useWorkbenchModels({ readModels, writeModels, logError })

    await m.loadModels('NewModel')

    expect(m.models.value).toBe('')
    // A never-written model counts as dirty so the first Save creates the file.
    expect(m.dirty.value).toBe(true)
  })

  it('creates a new model file on the first save even without edits', async () => {
    readModels.mockResolvedValueOnce(null)
    const m = useWorkbenchModels({ readModels, writeModels, logError })
    await m.loadModels('NewModel')

    await m.saveModels()

    expect(writeModels).toHaveBeenCalledWith('NewModel', '')
    expect(m.dirty.value).toBe(false)
    expect(m.saveStatus.value).toBe('saved')
  })

  it('writes the edited content on save and updates the savedAt timestamp', async () => {
    readModels.mockResolvedValueOnce('public class Foo {}')
    const m = useWorkbenchModels({ readModels, writeModels, logError })
    await m.loadModels('Foo')

    m.updateModels('public class FooRenamed {}')
    expect(m.dirty.value).toBe(true)

    await m.saveModels()

    expect(writeModels).toHaveBeenCalledWith('Foo', 'public class FooRenamed {}')
    expect(m.dirty.value).toBe(false)
    expect(m.saveStatus.value).toBe('saved')
    expect(m.lastSavedAt.value).toBeInstanceOf(Date)
  })

  it('surfaces an error status without throwing when the save fails', async () => {
    readModels.mockResolvedValueOnce('public class Foo {}')
    writeModels.mockRejectedValueOnce(new Error('boom'))
    const m = useWorkbenchModels({ readModels, writeModels, logError })
    await m.loadModels('Foo')

    m.updateModels('public class Flaky {}')
    await m.saveModels()

    expect(m.saveStatus.value).toBe('error')
    expect(logError).toHaveBeenCalledWith('failed to save models', { error: 'boom' })
  })

  it('falls back to a blank editor and logs when the load fails', async () => {
    readModels.mockRejectedValueOnce(new Error('network down'))
    const m = useWorkbenchModels({ readModels, writeModels, logError })

    await m.loadModels('Foo')

    expect(m.models.value).toBe('')
    expect(m.dirty.value).toBe(true) // not persisted, first save will create the file
    expect(logError).toHaveBeenCalledWith('failed to load models', { error: 'network down' })
  })

  it('ignores a stale load when the selection changes mid-flight', async () => {
    readModels.mockImplementationOnce(
      () => new Promise((resolve) => setTimeout(() => resolve('public class Slow {}'), 20)),
    )
    readModels.mockResolvedValueOnce('public class Fast {}')
    const m = useWorkbenchModels({ readModels, writeModels, logError })

    const slow = m.loadModels('SlowConstruct')
    await m.loadModels('FastConstruct')
    await slow

    expect(m.models.value).toBe('public class Fast {}')
  })

  it('clears the editor when loadModels is called with an empty name', async () => {
    const m = useWorkbenchModels({ readModels, writeModels, logError })
    await m.loadModels('Foo')
    const callsAfterFirstLoad = readModels.mock.calls.length

    await m.loadModels('')

    expect(m.models.value).toBe('')
    // The empty-name call must short-circuit — no additional readModels invocation.
    expect(readModels.mock.calls.length).toBe(callsAfterFirstLoad)
  })

  it('skips save when the model is clean and persisted', async () => {
    readModels.mockResolvedValueOnce('public class Foo {}')
    const m = useWorkbenchModels({ readModels, writeModels, logError })
    await m.loadModels('Foo')

    await m.saveModels()

    expect(writeModels).not.toHaveBeenCalled()
  })

  it('resets to idle when updateModels is called after a successful save', async () => {
    readModels.mockResolvedValueOnce('public class Foo {}')
    const m = useWorkbenchModels({ readModels, writeModels, logError })
    await m.loadModels('Foo')

    m.updateModels('public class Bar {}')
    await m.saveModels()
    expect(m.saveStatus.value).toBe('saved')

    m.updateModels('public class Bar {}')
    // Editing after a successful save drops the chip back to idle until the
    // next save completes.
    expect(m.saveStatus.value).toBe('idle')
  })
})
