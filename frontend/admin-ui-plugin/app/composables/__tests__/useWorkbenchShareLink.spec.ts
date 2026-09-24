import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  BODY_EDITOR_TAB_STORAGE_KEY,
  BODY_EDITOR_TABS,
  buildShareLinkUrl,
  readActiveBodyEditorTab,
  useWorkbenchShareLink,
} from '../useWorkbenchShareLink'

describe('useWorkbenchShareLink', () => {
  let writeText: ReturnType<typeof vi.fn>
  let clipboardSpy: ReturnType<typeof vi.spyOn>
  let toast: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> }
  let logError: ReturnType<typeof vi.fn>

  beforeEach(() => {
    writeText = vi.fn(async () => undefined)
    clipboardSpy = vi
      .spyOn(navigator, 'clipboard', 'get')
      .mockReturnValue({ writeText } as unknown as Clipboard)
    toast = { success: vi.fn(), error: vi.fn() }
    logError = vi.fn()
    window.localStorage.clear()
  })

  afterEach(() => {
    clipboardSpy.mockRestore()
    window.localStorage.clear()
  })

  it('builds the deep-link URL with the active tab + selected name', () => {
    const link = buildShareLinkUrl('alpha', 'preview')
    const parsed = new URL(link)
    expect(parsed.searchParams.get('objectName')).toBe('alpha')
    expect(parsed.searchParams.get('activeTab')).toBe('preview')
  })

  it('reads the active tab from localStorage', () => {
    window.localStorage.setItem(BODY_EDITOR_TAB_STORAGE_KEY, JSON.stringify('problems'))
    expect(readActiveBodyEditorTab()).toBe('problems')
  })

  it('falls back to structure when localStorage is empty or invalid', () => {
    expect(readActiveBodyEditorTab()).toBe('structure')
    window.localStorage.setItem(BODY_EDITOR_TAB_STORAGE_KEY, 'not json')
    expect(readActiveBodyEditorTab()).toBe('structure')
    window.localStorage.setItem(BODY_EDITOR_TAB_STORAGE_KEY, JSON.stringify('bogus'))
    expect(readActiveBodyEditorTab()).toBe('structure')
  })

  it('exposes the tab allow-list for callers to validate against', () => {
    expect(BODY_EDITOR_TABS).toEqual(['structure', 'code', 'preview', 'explain', 'problems'])
  })

  it('shareLink copies the URL to the clipboard and toasts on success', async () => {
    const { shareLink } = useWorkbenchShareLink({ toast, logError })
    await shareLink('alpha')
    expect(writeText).toHaveBeenCalledTimes(1)
    const link = (writeText.mock.calls[0] as unknown as [string])[0]
    const parsed = new URL(link)
    expect(parsed.searchParams.get('objectName')).toBe('alpha')
    expect(parsed.searchParams.get('activeTab')).toBe('structure')
    expect(toast.success).toHaveBeenCalled()
    expect(toast.error).not.toHaveBeenCalled()
  })

  it('shareLink is a no-op when the name is null', async () => {
    const { shareLink } = useWorkbenchShareLink({ toast, logError })
    await shareLink(null)
    expect(writeText).not.toHaveBeenCalled()
    expect(toast.success).not.toHaveBeenCalled()
  })

  it('shareLink toasts an error when the clipboard API is unavailable', async () => {
    clipboardSpy.mockReturnValue(undefined as unknown as Clipboard)
    const { shareLink } = useWorkbenchShareLink({ toast, logError })
    await shareLink('alpha')
    await flushPromises()
    expect(toast.error).toHaveBeenCalled()
    expect(logError).toHaveBeenCalled()
  })
})
