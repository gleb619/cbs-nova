import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, nextTick, reactive } from 'vue'

const saveError = new Error('save failed')

const mockWorkbench = {
  state: reactive({ isDirty: false, isSaving: false }),
  saveConstruct: vi.fn(),
  markDirty: vi.fn(() => {
    mockWorkbench.state.isDirty = true
  }),
  markClean: vi.fn(() => {
    mockWorkbench.state.isDirty = false
  }),
}

const mockDirty = {
  isDirty: computed(() => mockWorkbench.state.isDirty),
  markDirty: mockWorkbench.markDirty,
  markClean: mockWorkbench.markClean,
}

vi.mock('@cbs/admin-ui-plugin/composables/useDslWorkbench', () => ({
  useDslWorkbench: vi.fn(() => mockWorkbench),
}))

vi.mock('../useDraftDirty', () => ({
  useDraftDirty: vi.fn(() => mockDirty),
}))

import { useDraftSave } from '../useDraftSave'

describe('useDraftSave', () => {
  beforeEach(() => {
    mockWorkbench.state.isDirty = false
    mockWorkbench.state.isSaving = false
    mockWorkbench.saveConstruct.mockReset()
    mockWorkbench.markDirty.mockClear()
    mockWorkbench.markClean.mockClear()
  })

  it('starts in idle state', () => {
    const { status, lastSavedAt, error } = useDraftSave()

    expect(status.value).toBe('idle')
    expect(lastSavedAt.value).toBeNull()
    expect(error.value).toBeNull()
  })

  it('transitions dirty -> saving -> saved on a resolved save', async () => {
    mockWorkbench.saveConstruct.mockResolvedValueOnce(undefined)

    const { status, lastSavedAt, save } = useDraftSave()

    mockWorkbench.markDirty()
    await nextTick()
    expect(status.value).toBe('dirty')

    const savePromise = save()
    expect(status.value).toBe('saving')
    expect(lastSavedAt.value).toBeNull()

    await savePromise
    expect(status.value).toBe('saved')
    expect(lastSavedAt.value).toBeInstanceOf(Date)
    expect(mockWorkbench.markClean).toHaveBeenCalled()
  })

  it('transitions dirty -> saving -> error on a rejected save', async () => {
    mockWorkbench.saveConstruct.mockRejectedValueOnce(saveError)

    const { status, error, save } = useDraftSave()

    mockWorkbench.markDirty()
    await nextTick()
    expect(status.value).toBe('dirty')

    const savePromise = save()
    expect(status.value).toBe('saving')

    await savePromise
    expect(status.value).toBe('error')
    expect(error.value).toBe(saveError)
    expect(mockWorkbench.state.isDirty).toBe(true)
  })

  it('retry from error re-enters saving', async () => {
    mockWorkbench.saveConstruct
      .mockRejectedValueOnce(saveError)
      .mockResolvedValueOnce(undefined)

    const { status, save } = useDraftSave()

    mockWorkbench.markDirty()
    await save()
    expect(status.value).toBe('error')

    const retryPromise = save()
    expect(status.value).toBe('saving')

    await retryPromise
    expect(status.value).toBe('saved')
  })

  it('is a no-op when the draft is already clean', async () => {
    mockWorkbench.saveConstruct.mockResolvedValueOnce(undefined)

    const { status, save } = useDraftSave()

    expect(status.value).toBe('idle')
    await save()

    expect(status.value).toBe('idle')
    expect(mockWorkbench.saveConstruct).not.toHaveBeenCalled()
  })

  it('keeps dirty true and exposes error on network failure', async () => {
    mockWorkbench.saveConstruct.mockRejectedValueOnce(new TypeError('Network error'))

    const { status, error, save } = useDraftSave()

    mockWorkbench.markDirty()
    await save()

    expect(status.value).toBe('error')
    expect(error.value).toBeInstanceOf(Error)
    expect(mockWorkbench.state.isDirty).toBe(true)
  })

  it('returns to idle after the saved-recently window expires when clean', async () => {
    vi.useFakeTimers()
    mockWorkbench.saveConstruct.mockResolvedValueOnce(undefined)

    try {
      const { status, save } = useDraftSave()

      mockWorkbench.markDirty()
      await save()
      expect(status.value).toBe('saved')

      await vi.advanceTimersByTimeAsync(3_000)
      await nextTick()

      expect(status.value).toBe('idle')
    } finally {
      vi.useRealTimers()
    }
  })

  it('returns to dirty after the saved-recently window expires when edited again', async () => {
    vi.useFakeTimers()
    mockWorkbench.saveConstruct.mockResolvedValueOnce(undefined)

    try {
      const { status, save } = useDraftSave()

      mockWorkbench.markDirty()
      await save()
      expect(status.value).toBe('saved')

      mockWorkbench.markDirty()
      await nextTick()
      // Still within the saved-recently window, status stays 'saved'.
      expect(status.value).toBe('saved')

      await vi.advanceTimersByTimeAsync(3_000)
      await nextTick()

      expect(status.value).toBe('dirty')
    } finally {
      vi.useRealTimers()
    }
  })
})
