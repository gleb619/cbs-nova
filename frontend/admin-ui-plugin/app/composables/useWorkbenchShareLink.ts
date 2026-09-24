import type { useToast } from '@cbs/components/composables'

export const BODY_EDITOR_TABS = [
  'structure',
  'code',
  'preview',
  'explain',
  'hierarchy',
  'problems',
] as const
export type BodyEditorTab = (typeof BODY_EDITOR_TABS)[number]

export const BODY_EDITOR_TAB_STORAGE_KEY = 'cbs-nova:body-editor:active-tab'

export interface UseWorkbenchShareLinkOptions {
  toast: ReturnType<typeof useToast>
  /** Reports failures to the page logger. */
  logError: (message: string, fields?: Record<string, unknown>) => void
}

/**
 * Share Link — builds a deep-link URL for the current selection + open body
 * editor tab and copies it to the clipboard. Active tab is read at click time
 * from the body editor's localStorage (it owns that state and does not emit).
 *
 * Decision: extracted from `dsl-workbench.vue` so the page stays a thin
 * orchestrator. The body-editor tab contract (localStorage key + allowed set)
 * lives alongside the reader so callers don't drift.
 */
export function readActiveBodyEditorTab(): BodyEditorTab {
  if (typeof window === 'undefined') return 'structure'
  try {
    const raw = window.localStorage.getItem(BODY_EDITOR_TAB_STORAGE_KEY)
    if (!raw) return 'structure'
    const parsed = JSON.parse(raw) as unknown
    if (typeof parsed === 'string' && (BODY_EDITOR_TABS as readonly string[]).includes(parsed)) {
      return parsed as BodyEditorTab
    }
  } catch {
    // fall through to default
  }
  return 'structure'
}

export function buildShareLinkUrl(name: string, tab: BodyEditorTab): string {
  const url = new URL(window.location.href)
  url.search = ''
  url.searchParams.set('objectName', name)
  url.searchParams.set('activeTab', tab)
  return url.toString()
}

export function useWorkbenchShareLink(options: UseWorkbenchShareLinkOptions) {
  const { toast, logError } = options

  async function shareLink(name: string | null): Promise<void> {
    if (!name) return
    const tab = readActiveBodyEditorTab()
    const link = buildShareLinkUrl(name, tab)
    try {
      if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(link)
      } else {
        throw new Error('Clipboard API unavailable')
      }
      toast.success(`Copied to a clipboard!`)
    } catch (err) {
      logError('failed to copy share link', { error: (err as Error).message })
      toast.error('Could not copy share link to clipboard.')
    }
  }

  return { shareLink, readActiveBodyEditorTab }
}
